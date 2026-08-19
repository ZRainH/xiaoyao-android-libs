package com.xiaoyao.modbus.frame

import com.xiaoyao.modbus.crc.ModbusCrc
import com.xiaoyao.modbus.exception.ModbusException
import com.xiaoyao.modbus.model.ModbusExceptionCode
import com.xiaoyao.modbus.model.ModbusFunctionCode

/**
 * Modbus RTU **响应解析**。
 *
 * 在传输层已完成粘包拆帧与严格匹配后，本类负责：
 * - 再次做基础合法性检查（CRC/从站/功能码/异常）
 * - 把数据区解成业务对象（线圈位、寄存器值等）
 */
object ModbusFrameParser {

    /**
     * 校验响应帧基础字段。
     * 若功能码最高位为 1，抛出 [ModbusException.EXCEPTION_RESPONSE]。
     */
    fun ensureValid(frame: ByteArray, expectedSlaveId: Int, expectedFunction: Int) {
        if (frame.size < 5) {
            throw ModbusException(ModbusException.INVALID_RESPONSE, "response too short: ${frame.size}")
        }
        if (!ModbusCrc.verify(frame)) {
            throw ModbusException(ModbusException.CRC_ERROR, "CRC check failed")
        }
        val slaveId = frame[0].toInt() and 0xFF
        if (expectedSlaveId != 0 && slaveId != expectedSlaveId) {
            throw ModbusException(
                ModbusException.INVALID_RESPONSE,
                "slaveId mismatch: expect=$expectedSlaveId actual=$slaveId"
            )
        }
        val function = frame[1].toInt() and 0xFF
        // 异常响应：func = 原功能码 | 0x80，下一字节为异常码
        if (function and 0x80 != 0) {
            val exCode = if (frame.size >= 3) frame[2].toInt() and 0xFF else -1
            val mapped = ModbusExceptionCode.from(exCode)
            throw ModbusException(
                code = ModbusException.EXCEPTION_RESPONSE,
                message = "exception 0x${exCode.toString(16)}: ${mapped.description}",
                exceptionCode = mapped
            )
        }
        if (function != expectedFunction) {
            throw ModbusException(
                ModbusException.INVALID_RESPONSE,
                "function mismatch: expect=$expectedFunction actual=$function"
            )
        }
    }

    /**
     * 解析读线圈/离散量应答数据区。
     * 每个 bit 对应一个线圈，字节内低位先对应靠前地址。
     */
    fun parseReadBits(frame: ByteArray, quantity: Int): BooleanArray {
        val byteCount = frame[2].toInt() and 0xFF
        if (frame.size < 3 + byteCount + 2) {
            throw ModbusException(ModbusException.INVALID_RESPONSE, "bit response length invalid")
        }
        val result = BooleanArray(quantity)
        for (i in 0 until quantity) {
            val b = frame[3 + i / 8].toInt() and 0xFF
            result[i] = (b shr (i % 8)) and 0x01 == 1
        }
        return result
    }

    /**
     * 解析读寄存器应答：从 byte 3 开始，每 2 字节大端组成一个寄存器值。
     */
    fun parseReadRegisters(frame: ByteArray, quantity: Int): IntArray {
        val byteCount = frame[2].toInt() and 0xFF
        if (byteCount != quantity * 2 || frame.size < 3 + byteCount + 2) {
            throw ModbusException(ModbusException.INVALID_RESPONSE, "register response length invalid")
        }
        val result = IntArray(quantity)
        for (i in 0 until quantity) {
            val hi = frame[3 + i * 2].toInt() and 0xFF
            val lo = frame[3 + i * 2 + 1].toInt() and 0xFF
            result[i] = (hi shl 8) or lo
        }
        return result
    }

    /**
     * 解析写操作回显：`(地址, 值或数量)`，均为大端。
     * 适用于 05/06/0F/10 的正常应答。
     */
    fun parseWriteEchoAddressQuantity(frame: ByteArray): Pair<Int, Int> {
        if (frame.size < 8) {
            throw ModbusException(ModbusException.INVALID_RESPONSE, "write echo too short")
        }
        val address = ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
        val quantityOrValue = ((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)
        return address to quantityOrValue
    }

    /** 各功能码正常应答的最小长度（含 CRC），供参考 */
    fun expectedMinLength(functionCode: Int): Int = when (functionCode) {
        ModbusFunctionCode.READ_COILS,
        ModbusFunctionCode.READ_DISCRETE_INPUTS,
        ModbusFunctionCode.READ_HOLDING_REGISTERS,
        ModbusFunctionCode.READ_INPUT_REGISTERS -> 5
        ModbusFunctionCode.WRITE_SINGLE_COIL,
        ModbusFunctionCode.WRITE_SINGLE_REGISTER,
        ModbusFunctionCode.WRITE_MULTIPLE_COILS,
        ModbusFunctionCode.WRITE_MULTIPLE_REGISTERS -> 8
        else -> 5
    }
}
