package com.beverage.demo.protocol

/**
 * 从起始符到内容末字节累加和，取低 8 位。
 */
object WxChecksum {
    fun calc(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        var sum = 0
        val end = offset + length
        for (i in offset until end) {
            sum = (sum + (bytes[i].toInt() and 0xFF)) and 0xFF
        }
        return sum
    }

    fun verify(frame: ByteArray): Boolean {
        if (frame.size < WxProtocol.HEADER_SIZE + 1) return false
        val bodyLen = frame.size - 1
        return calc(frame, 0, bodyLen) == (frame[frame.size - 1].toInt() and 0xFF)
    }
}
