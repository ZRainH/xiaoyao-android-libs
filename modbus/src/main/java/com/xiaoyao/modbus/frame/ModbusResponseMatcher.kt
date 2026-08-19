package com.xiaoyao.modbus.frame

import com.xiaoyao.modbus.crc.ModbusCrc
import com.xiaoyao.modbus.exception.ModbusException
import com.xiaoyao.modbus.model.ModbusExceptionCode
import com.xiaoyao.modbus.model.ModbusFunctionCode
import com.xiaoyao.modbus.model.ModbusRequestContext

/**
 * 请求/响应一一对应校验器。
 *
 * - [softMatch]：拆帧阶段快速判断“像不像本次应答”（从站 + 功能码）
 * - [assertMatches]：收齐帧后做严格字段校验（数量、回显地址/值等）
 *
 * 只有两者都通过，才认为这一帧就是本次请求的合法应答。
 */
object ModbusResponseMatcher {

    /**
     * 粗匹配：用于粘包滑动窗口中筛选候选帧。
     *
     * 条件：
     * - 从站地址一致（期望为 0 时不校验，兼容广播场景）
     * - 功能码等于请求功能码，或等于异常功能码（请求功能码 | 0x80）
     */
    fun softMatch(frame: ByteArray, ctx: ModbusRequestContext): Boolean {
        if (frame.size < 2) return false
        val slaveId = frame[0].toInt() and 0xFF
        val function = frame[1].toInt() and 0xFF
        if (ctx.slaveId != 0 && slaveId != ctx.slaveId) return false
        return function == ctx.functionCode || function == ctx.exceptionFunctionCode
    }

    /**
     * 严格匹配：确认应答内容与 [ctx] 描述的请求一一对应。
     *
     * 校验顺序：
     * 1. 长度 / CRC
     * 2. 从站地址、功能码（含异常响应）
     * 3. 按功能码校验业务字段（byteCount、写回显等）
     *
     * @throws ModbusException 任一字段对不上
     */
    fun assertMatches(frame: ByteArray, ctx: ModbusRequestContext) {
        if (frame.size < 5) {
            throw ModbusException(ModbusException.INVALID_RESPONSE, "response too short")
        }
        if (!ModbusCrc.verify(frame)) {
            throw ModbusException(ModbusException.CRC_ERROR, "CRC check failed")
        }

        val slaveId = frame[0].toInt() and 0xFF
        val function = frame[1].toInt() and 0xFF

        if (ctx.slaveId != 0 && slaveId != ctx.slaveId) {
            throw ModbusException(
                ModbusException.INVALID_RESPONSE,
                "slave mismatch: expect=${ctx.slaveId} actual=$slaveId"
            )
        }

        // 异常响应：已确认是对本请求功能码的异常应答，直接抛业务失败。
        // 切勿静默 return——写接口若未再调 ensureValid，会把从站拒绝当成写成功。
        if (function == ctx.exceptionFunctionCode) {
            if (frame.size != 5) {
                throw ModbusException(
                    ModbusException.INVALID_RESPONSE,
                    "exception frame length mismatch: ${frame.size}"
                )
            }
            val exCode = frame[2].toInt() and 0xFF
            val mapped = ModbusExceptionCode.from(exCode)
            throw ModbusException(
                code = ModbusException.EXCEPTION_RESPONSE,
                message = "exception 0x${exCode.toString(16)}: ${mapped.description}",
                exceptionCode = mapped
            )
        }
        if (function != ctx.functionCode) {
            throw ModbusException(
                ModbusException.INVALID_RESPONSE,
                "function mismatch: expect=${ctx.functionCode} actual=$function"
            )
        }

        when (function) {
            // 读线圈/离散量：byteCount 必须等于 ceil(quantity/8)
            ModbusFunctionCode.READ_COILS,
            ModbusFunctionCode.READ_DISCRETE_INPUTS -> {
                val byteCount = frame[2].toInt() and 0xFF
                val expectBytes = (ctx.quantity + 7) / 8
                if (byteCount != expectBytes) {
                    throw ModbusException(
                        ModbusException.INVALID_RESPONSE,
                        "coil byteCount mismatch: expect=$expectBytes actual=$byteCount"
                    )
                }
                if (frame.size != 3 + byteCount + 2) {
                    throw ModbusException(ModbusException.INVALID_RESPONSE, "coil frame length mismatch")
                }
            }

            // 读寄存器：byteCount 必须等于 quantity*2
            ModbusFunctionCode.READ_HOLDING_REGISTERS,
            ModbusFunctionCode.READ_INPUT_REGISTERS -> {
                val byteCount = frame[2].toInt() and 0xFF
                val expectBytes = ctx.quantity * 2
                if (byteCount != expectBytes) {
                    throw ModbusException(
                        ModbusException.INVALID_RESPONSE,
                        "register byteCount mismatch: expect=$expectBytes actual=$byteCount"
                    )
                }
                if (frame.size != 3 + byteCount + 2) {
                    throw ModbusException(ModbusException.INVALID_RESPONSE, "register frame length mismatch")
                }
            }

            // 写单线圈：回显地址 + FF00/0000
            ModbusFunctionCode.WRITE_SINGLE_COIL -> {
                val (addr, value) = echoAddressValue(frame)
                val expectValue = if (ctx.coilOn == true) 0xFF00 else 0x0000
                if (addr != ctx.address || value != expectValue) {
                    throw ModbusException(
                        ModbusException.INVALID_RESPONSE,
                        "write coil echo mismatch: addr=$addr value=0x${value.toString(16)}"
                    )
                }
            }

            // 写单寄存器：回显地址 + 寄存器值
            ModbusFunctionCode.WRITE_SINGLE_REGISTER -> {
                val (addr, value) = echoAddressValue(frame)
                val expectValue = ctx.registerValue ?: -1
                if (addr != ctx.address || value != expectValue) {
                    throw ModbusException(
                        ModbusException.INVALID_RESPONSE,
                        "write register echo mismatch: addr=$addr value=$value"
                    )
                }
            }

            // 写多个：回显起始地址 + 数量
            ModbusFunctionCode.WRITE_MULTIPLE_COILS,
            ModbusFunctionCode.WRITE_MULTIPLE_REGISTERS -> {
                val (addr, qty) = echoAddressValue(frame)
                if (addr != ctx.address || qty != ctx.quantity) {
                    throw ModbusException(
                        ModbusException.INVALID_RESPONSE,
                        "write multiple echo mismatch: addr=$addr qty=$qty"
                    )
                }
            }
        }
    }

    /** 解析写操作应答中的地址(大端)与值/数量(大端) */
    private fun echoAddressValue(frame: ByteArray): Pair<Int, Int> {
        if (frame.size < 8) {
            throw ModbusException(ModbusException.INVALID_RESPONSE, "echo frame too short")
        }
        val address = ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
        val value = ((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)
        return address to value
    }
}
