package com.xiaoyao.modbus.frame

import com.xiaoyao.modbus.crc.ModbusCrc
import com.xiaoyao.modbus.model.ModbusFunctionCode
import com.xiaoyao.modbus.model.ModbusRequestContext

/**
 * RTU 接收缓冲拆帧器（处理半包与粘包）。
 *
 * ## 定界规则（优先级从高到低）
 * 1. 根据功能码推算期望长度（读用 byteCount，写固定 8，异常固定 5）
 * 2. 长度够后做 CRC 校验
 * 3. 用 [ModbusResponseMatcher.softMatch] 判断是否为本请求应答
 * 4. CRC 失败：滑动 1 字节重新找帧头
 * 5. CRC 成功但不是本次应答：整帧跳过（粘包中的他站/旧帧）
 *
 * 注意：3.5 字符空闲由传输层负责等待更多字节，本类不做时间定界。
 */
object ModbusRtuFrameExtractor {

    /**
     * 根据缓冲区前缀推算“完整响应帧”应有的字节数。
     *
     * @return 期望总长度；前缀信息不足时返回 null（例如还没读到 byteCount）
     */
    fun expectedLength(buffer: ByteArray, offset: Int = 0, size: Int = buffer.size - offset): Int? {
        if (size < 2) return null
        val function = buffer[offset + 1].toInt() and 0xFF

        // 异常响应：addr(1) + (func|0x80)(1) + exCode(1) + CRC(2) = 5
        if (function and 0x80 != 0) {
            return 5
        }

        return when (function) {
            ModbusFunctionCode.READ_COILS,
            ModbusFunctionCode.READ_DISCRETE_INPUTS,
            ModbusFunctionCode.READ_HOLDING_REGISTERS,
            ModbusFunctionCode.READ_INPUT_REGISTERS -> {
                if (size < 3) return null
                val byteCount = buffer[offset + 2].toInt() and 0xFF
                // 防御异常/噪声导致的超大长度
                if (byteCount > 250) return null
                // addr + func + byteCount + data + CRC
                3 + byteCount + 2
            }

            // 05/06/0F/10 正常应答均为 8 字节（含 CRC）
            ModbusFunctionCode.WRITE_SINGLE_COIL,
            ModbusFunctionCode.WRITE_SINGLE_REGISTER,
            ModbusFunctionCode.WRITE_MULTIPLE_COILS,
            ModbusFunctionCode.WRITE_MULTIPLE_REGISTERS -> 8

            else -> null
        }
    }

    /** 兼容旧调用：仅按从站+功能码构造临时上下文 */
    fun tryExtract(
        buffer: ByteArray,
        expectedSlaveId: Int,
        expectedFunction: Int
    ): ExtractResult = tryExtract(
        buffer,
        ModbusRequestContext(
            slaveId = expectedSlaveId,
            functionCode = expectedFunction,
            address = 0
        )
    )

    /**
     * 从 [buffer] 中尝试切出与 [context] 对应的一帧。
     *
     * @return [ExtractResult.frame] 非空表示成功；
     *         [ExtractResult.consumed] 为应从接收缓冲丢弃的字节数（含噪声前缀）；
     *         [ExtractResult.needMore] 为 true 表示还需要更多字节（半包）。
     */
    fun tryExtract(
        buffer: ByteArray,
        context: ModbusRequestContext
    ): ExtractResult {
        if (buffer.isEmpty()) {
            return ExtractResult(frame = null, consumed = 0, needMore = true)
        }

        var offset = 0
        while (offset < buffer.size) {
            val remain = buffer.size - offset
            val expected = expectedLength(buffer, offset, remain)

            when {
                // 已知长度但数据不够 → 半包，等待后续字节
                expected != null && remain < expected -> {
                    return if (offset == 0) {
                        ExtractResult(frame = null, consumed = 0, needMore = true)
                    } else {
                        // 前面已跳过噪声，保留从 offset 起的半包
                        ExtractResult(frame = null, consumed = offset, needMore = true)
                    }
                }

                // 已知长度且数据足够 → 验 CRC，再看是否匹配本次请求
                expected != null -> {
                    val candidate = buffer.copyOfRange(offset, offset + expected)
                    if (ModbusCrc.verify(candidate)) {
                        if (ModbusResponseMatcher.softMatch(candidate, context)) {
                            return ExtractResult(
                                frame = candidate,
                                consumed = offset + expected,
                                needMore = false
                            )
                        }
                        // 完整但非本次应答（粘包里的旧帧/他站）：整帧跳过
                        offset += expected
                    } else {
                        // CRC 失败：可能帧头没对齐，滑 1 字节再试
                        offset++
                    }
                }

                // 未知功能码：退化为 CRC 扫描（5..256）
                else -> {
                    val scanned = scanByCrc(buffer, offset, context)
                    if (scanned != null) {
                        return ExtractResult(
                            frame = scanned.frame,
                            consumed = offset + scanned.end,
                            needMore = false
                        )
                    }
                    if (remain < 5) {
                        return ExtractResult(frame = null, consumed = offset, needMore = true)
                    }
                    offset++
                }
            }
        }

        // 整段滑完仍无有效帧：丢弃全部，避免死循环卡在噪声上
        return ExtractResult(frame = null, consumed = buffer.size, needMore = true)
    }

    private data class Scanned(val frame: ByteArray, val end: Int)

    /** 在未知功能码时，用 CRC 暴力尝试可能的帧长度 */
    private fun scanByCrc(
        buffer: ByteArray,
        offset: Int,
        context: ModbusRequestContext
    ): Scanned? {
        val maxLen = minOf(buffer.size - offset, 256)
        for (len in 5..maxLen) {
            val candidate = buffer.copyOfRange(offset, offset + len)
            if (ModbusCrc.verify(candidate) && ModbusResponseMatcher.softMatch(candidate, context)) {
                return Scanned(candidate, len)
            }
        }
        return null
    }

    /**
     * @param frame 切出的完整帧；失败时为 null
     * @param consumed 应从接收缓冲头部移除的字节数
     * @param needMore 是否还需要继续收字节
     */
    data class ExtractResult(
        val frame: ByteArray?,
        val consumed: Int,
        val needMore: Boolean
    )
}
