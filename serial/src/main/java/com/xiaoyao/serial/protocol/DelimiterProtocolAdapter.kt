package com.xiaoyao.serial.protocol

/**
 * 分隔符协议：按 delimiter 切帧（如 `\n`、`\r\n`、自定义 `0xFF 0xFE`）。
 *
 * @param delimiter 帧结束标记
 * @param includeDelimiter 输出帧是否保留分隔符
 * @param maxFrameSize 业务帧最大长度（不含/含分隔符取决于 [includeDelimiter]）
 */
class DelimiterProtocolAdapter(
    private val delimiter: ByteArray,
    private val includeDelimiter: Boolean = false,
    private val maxFrameSize: Int = 8 * 1024,
    override val name: String = "delimiter"
) : SerialProtocolAdapter {

    /** 缓冲物理上限：载荷上限 + 分隔符（当分隔符不计入 frameLen 时） */
    private val maxBufferSize: Int =
        if (includeDelimiter) maxFrameSize else maxFrameSize + delimiter.size

    init {
        require(delimiter.isNotEmpty()) { "delimiter must not be empty" }
        require(maxFrameSize >= 1) { "maxFrameSize too small" }
        require(maxBufferSize > delimiter.size) { "maxBufferSize too small" }
    }

    private val buffer = FrameBuffer()

    override fun encode(payload: ByteArray): ByteArray {
        val out = ByteArray(payload.size + delimiter.size)
        System.arraycopy(payload, 0, out, 0, payload.size)
        System.arraycopy(delimiter, 0, out, payload.size, delimiter.size)
        return out
    }

    override fun feed(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()
        val frames = ArrayList<ByteArray>()
        for (i in chunk.indices) {
            buffer.append(chunk[i])
            if (buffer.endsWith(delimiter)) {
                val total = buffer.size
                val frameLen = if (includeDelimiter) total else total - delimiter.size
                if (frameLen in 1..maxFrameSize) {
                    frames += buffer.copyOfRange(0, frameLen)
                }
                buffer.clear()
            } else if (buffer.size > maxBufferSize) {
                do {
                    resyncAfterOverflow()
                } while (buffer.size > maxBufferSize && buffer.indexOf(delimiter) >= 0)
            }
        }
        return frames
    }

    override fun reset() {
        buffer.clear()
    }

    /**
     * 超长无分隔符：丢弃到下一个 delimiter（含），或仅保留可能的半个 delimiter，便于后续重同步。
     */
    private fun resyncAfterOverflow() {
        val found = buffer.indexOf(delimiter)
        if (found >= 0) {
            buffer.discardFront(found + delimiter.size)
            return
        }
        val keep = (delimiter.size - 1).coerceAtLeast(0)
        if (buffer.size > keep) {
            buffer.discardFront(buffer.size - keep)
        }
    }

    companion object {
        fun line(includeDelimiter: Boolean = false): DelimiterProtocolAdapter =
            DelimiterProtocolAdapter(
                delimiter = byteArrayOf('\n'.code.toByte()),
                includeDelimiter = includeDelimiter,
                name = "line"
            )

        fun crlf(includeDelimiter: Boolean = false): DelimiterProtocolAdapter =
            DelimiterProtocolAdapter(
                delimiter = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()),
                includeDelimiter = includeDelimiter,
                name = "crlf"
            )
    }
}
