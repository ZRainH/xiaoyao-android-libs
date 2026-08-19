package com.beverage.demo.protocol

/**
 * 通用帧（已解析）。
 *
 * 字节布局见《协议文档》：起始符 WX | 地址 | 屏幕指令 | 消息ID | 功能类型 | 内容长度 | 内容 | 校验。
 */
data class WxFrame(
    val address: Int,
    val screenCommand: Int,
    val messageId: Int,
    val functionCode: Int,
    val content: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WxFrame) return false
        return address == other.address &&
            screenCommand == other.screenCommand &&
            messageId == other.messageId &&
            functionCode == other.functionCode &&
            content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = address
        result = 31 * result + screenCommand
        result = 31 * result + messageId
        result = 31 * result + functionCode
        result = 31 * result + content.contentHashCode()
        return result
    }
}

data class DispenseSegment(
    val pin: Int,
    /** true=正向 0x01，false=反向 0x00 */
    val forward: Boolean,
    val speed: Int,
    /** 运行时间，毫秒 */
    val durationMs: Int
)

data class PinStatus(
    val pin: Int,
    val status: Int
)

sealed class WxPayload {
    data class PinDispenseRequest(
        val pin: Int,
        val forward: Boolean,
        val speed: Int,
        val open: Boolean
    ) : WxPayload()

    data class PinDispenseResponse(
        val pin: Int,
        val status: Int
    ) : WxPayload()

    data object StopRequest : WxPayload()

    data class StopResponse(val status: Int) : WxPayload()

    data class SegmentDispenseRequest(
        val segments: List<DispenseSegment>
    ) : WxPayload()

    data class SegmentDispenseResponse(
        val items: List<PinStatus>
    ) : WxPayload()

    data class DispenseFinished(val status: Int) : WxPayload()

    data class CleanRequest(
        val totalPin: Int,
        val totalForward: Boolean,
        val speed: Int,
        val loopPin: Int,
        /** 0x00 反向 / 0x01 正向 / 0x02 空运 */
        val loopDirection: Int,
        val loopDurationMs: Int
    ) : WxPayload()

    data class CleanResponse(
        val pin: Int,
        val status: Int
    ) : WxPayload()

    data class CleanFinished(val status: Int) : WxPayload()

    data class Raw(val functionCode: Int, val content: ByteArray) : WxPayload() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Raw) return false
            return functionCode == other.functionCode && content.contentEquals(other.content)
        }

        override fun hashCode(): Int = 31 * functionCode + content.contentHashCode()
    }
}

data class WxMessage(
    val frame: WxFrame,
    val payload: WxPayload
)
