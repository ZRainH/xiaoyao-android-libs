package com.xiaoyao.serial.protocol

/**
 * 长度字段协议：`[header][length][payload]`。
 *
 * [lengthOffset] 必须等于 [header].size，以保证 [encode] / [feed] 对称
 *（length 紧跟 header；不支持中间插入 Command 等字段的自动组帧）。
 *
 * @param lengthSize length 字段字节数（1 或 2）
 * @param lengthIncludesHeader true 表示 length 含头+长度字段本身；false 表示 length 仅为 payload 长度
 * @param bigEndian length 是否大端
 */
class LengthFieldProtocolAdapter(
    private val lengthOffset: Int = 0,
    private val lengthSize: Int = 1,
    private val lengthIncludesHeader: Boolean = false,
    private val header: ByteArray = ByteArray(0),
    private val bigEndian: Boolean = true,
    private val maxFrameSize: Int = 8 * 1024,
    override val name: String = "length-field"
) : SerialProtocolAdapter {

    init {
        require(lengthSize == 1 || lengthSize == 2) { "lengthSize must be 1 or 2" }
        require(lengthOffset >= 0) { "lengthOffset must be >= 0" }
        require(lengthOffset == header.size) {
            "lengthOffset ($lengthOffset) must equal header.size (${header.size}) " +
                "so encode/decode stay symmetric"
        }
        require(maxFrameSize >= lengthOffset + lengthSize) { "maxFrameSize too small" }
    }

    private val buffer = FrameBuffer()

    override fun encode(payload: ByteArray): ByteArray {
        val headerLen = header.size
        val lengthValue = if (lengthIncludesHeader) {
            headerLen + lengthSize + payload.size
        } else {
            payload.size
        }
        val out = ByteArray(headerLen + lengthSize + payload.size)
        var pos = 0
        if (header.isNotEmpty()) {
            System.arraycopy(header, 0, out, 0, headerLen)
            pos = headerLen
        }
        when (lengthSize) {
            1 -> out[pos] = (lengthValue and 0xFF).toByte()
            2 -> {
                if (bigEndian) {
                    out[pos] = ((lengthValue ushr 8) and 0xFF).toByte()
                    out[pos + 1] = (lengthValue and 0xFF).toByte()
                } else {
                    out[pos] = (lengthValue and 0xFF).toByte()
                    out[pos + 1] = ((lengthValue ushr 8) and 0xFF).toByte()
                }
            }
        }
        pos += lengthSize
        System.arraycopy(payload, 0, out, pos, payload.size)
        return out
    }

    override fun feed(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()
        buffer.append(chunk)
        val frames = ArrayList<ByteArray>()
        while (true) {
            val frame = tryExtract() ?: break
            frames += frame
        }
        return frames
    }

    override fun reset() {
        buffer.clear()
    }

    private fun tryExtract(): ByteArray? {
        val minLen = lengthOffset + lengthSize

        while (true) {
            if (header.isNotEmpty()) {
                val idx = buffer.indexOf(header)
                if (idx < 0) {
                    // 保留可能的半个 header
                    if (buffer.size > header.size) {
                        val keep = header.size - 1
                        val start = buffer.size - keep
                        val tail = buffer.copyOfRange(start, buffer.size)
                        buffer.clear()
                        buffer.append(tail)
                    }
                    return null
                }
                if (idx > 0) {
                    buffer.discardFront(idx)
                }
            }

            if (buffer.size < minLen) return null

            val lengthValue = readLength()
            if (lengthValue < 0 || lengthValue > maxFrameSize) {
                buffer.discardFront(1)
                continue
            }

            val total = if (lengthIncludesHeader) {
                lengthValue
            } else {
                lengthOffset + lengthSize + lengthValue
            }
            // total 必须至少覆盖到 length 字段末尾，避免抽出过短残片导致失步
            if (total < minLen || total > maxFrameSize) {
                buffer.discardFront(1)
                continue
            }
            if (buffer.size < total) return null

            val frame = buffer.copyOfRange(0, total)
            buffer.discardFront(total)
            return frame
        }
    }

    private fun readLength(): Int {
        val b0 = buffer[lengthOffset].toInt() and 0xFF
        return when (lengthSize) {
            1 -> b0
            else -> {
                val b1 = buffer[lengthOffset + 1].toInt() and 0xFF
                if (bigEndian) (b0 shl 8) or b1 else (b1 shl 8) or b0
            }
        }
    }
}
