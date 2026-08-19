package com.xiaoyao.serial.protocol

/**
 * 透传适配器：每个读到的 chunk 即为一帧，不做粘包拆分。
 */
object RawProtocolAdapter : SerialProtocolAdapter {
    override val name: String = "raw"

    override fun feed(chunk: ByteArray): List<ByteArray> =
        if (chunk.isEmpty()) emptyList() else listOf(chunk)

    override fun reset() = Unit
}
