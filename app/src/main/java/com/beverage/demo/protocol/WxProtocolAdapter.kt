package com.beverage.demo.protocol

import com.xiaoyao.serial.protocol.SerialProtocolAdapter

/**
 * WX 粘包拆帧：定位 `57 58`，按内容长度切完整帧并校验。
 */
class WxProtocolAdapter(
    override val name: String = "wx"
) : SerialProtocolAdapter {

    private val buffer = ArrayList<Byte>(128)

    override fun encode(payload: ByteArray): ByteArray = payload

    override fun feed(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()
        chunk.forEach { buffer.add(it) }
        val frames = ArrayList<ByteArray>()
        while (true) {
            val frame = tryExtract() ?: break
            frames += frame
        }
        // 防止无限膨胀
        if (buffer.size > MAX_BUFFER) {
            buffer.clear()
        }
        return frames
    }

    override fun reset() {
        buffer.clear()
    }

    private fun tryExtract(): ByteArray? {
        val headerIndex = indexOfHeader()
        if (headerIndex < 0) {
            if (buffer.size > 1) {
                // 保留可能的半个 'W'
                val last = buffer.last()
                buffer.clear()
                if (last == WxProtocol.HEADER[0]) buffer.add(last)
            }
            return null
        }
        if (headerIndex > 0) {
            repeat(headerIndex) { buffer.removeAt(0) }
        }
        if (buffer.size < WxProtocol.HEADER_SIZE) return null

        val contentLen = buffer[7].toInt() and 0xFF
        val total = WxProtocol.HEADER_SIZE + contentLen + 1
        if (total > MAX_FRAME) {
            buffer.removeAt(0)
            return null
        }
        if (buffer.size < total) return null

        val frame = ByteArray(total) { i -> buffer[i] }
        if (!WxChecksum.verify(frame)) {
            // 假同步：丢掉 1 字节继续找帧头
            buffer.removeAt(0)
            return null
        }
        repeat(total) { buffer.removeAt(0) }
        return frame
    }

    private fun indexOfHeader(): Int {
        for (i in 0 until buffer.size - 1) {
            if (buffer[i] == WxProtocol.HEADER[0] && buffer[i + 1] == WxProtocol.HEADER[1]) {
                return i
            }
        }
        return -1
    }

    companion object {
        private const val MAX_BUFFER = 4096
        private const val MAX_FRAME = 512
    }
}
