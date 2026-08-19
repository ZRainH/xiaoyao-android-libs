package com.beverage.demo.protocol

/**
 * WX 协议组帧 / 拆帧。
 */
object WxCodec {

    fun buildFrame(
        functionCode: Int,
        content: ByteArray,
        address: Int = WxProtocol.DEFAULT_ADDRESS,
        screenCommand: Int = WxProtocol.DEFAULT_SCREEN,
        messageId: Int = 1
    ): ByteArray {
        require(content.size <= 255) { "content too long: ${content.size}" }
        val out = ByteArray(WxProtocol.HEADER_SIZE + content.size + 1)
        var i = 0
        out[i++] = WxProtocol.HEADER[0]
        out[i++] = WxProtocol.HEADER[1]
        out[i++] = (address and 0xFF).toByte()
        out[i++] = (screenCommand and 0xFF).toByte()
        out[i++] = ((messageId ushr 8) and 0xFF).toByte()
        out[i++] = (messageId and 0xFF).toByte()
        out[i++] = (functionCode and 0xFF).toByte()
        out[i++] = (content.size and 0xFF).toByte()
        System.arraycopy(content, 0, out, i, content.size)
        i += content.size
        out[i] = WxChecksum.calc(out, 0, i).toByte()
        return out
    }

    fun parseFrame(raw: ByteArray): WxFrame {
        if (raw.size < WxProtocol.HEADER_SIZE + 1) {
            throw IllegalArgumentException("frame too short: ${raw.size}")
        }
        if (raw[0] != WxProtocol.HEADER[0] || raw[1] != WxProtocol.HEADER[1]) {
            throw IllegalArgumentException("invalid header")
        }
        if (!WxChecksum.verify(raw)) {
            throw IllegalArgumentException("checksum mismatch")
        }
        val contentLen = raw[7].toInt() and 0xFF
        val expect = WxProtocol.HEADER_SIZE + contentLen + 1
        if (raw.size != expect) {
            throw IllegalArgumentException("length mismatch: size=${raw.size} expect=$expect")
        }
        val content = if (contentLen == 0) {
            ByteArray(0)
        } else {
            raw.copyOfRange(8, 8 + contentLen)
        }
        return WxFrame(
            address = raw[2].toInt() and 0xFF,
            screenCommand = raw[3].toInt() and 0xFF,
            messageId = ((raw[4].toInt() and 0xFF) shl 8) or (raw[5].toInt() and 0xFF),
            functionCode = raw[6].toInt() and 0xFF,
            content = content
        )
    }

    fun encodePayload(payload: WxPayload): Pair<Int, ByteArray> = when (payload) {
        is WxPayload.PinDispenseRequest -> WxFunctionCode.PIN_DISPENSE to byteArrayOf(
            payload.pin.toByte(),
            if (payload.forward) 0x01 else 0x00,
            ((payload.speed ushr 8) and 0xFF).toByte(),
            (payload.speed and 0xFF).toByte(),
            if (payload.open) 0x01 else 0x00
        )

        is WxPayload.StopRequest -> WxFunctionCode.STOP_DISPENSE to ByteArray(0)

        is WxPayload.SegmentDispenseRequest -> {
            val segments = payload.segments
            val segmentBytes = ByteArray(segments.size * 7)
            var p = 0
            for (seg in segments) {
                segmentBytes[p++] = (seg.pin and 0xFF).toByte()
                segmentBytes[p++] = if (seg.forward) 0x01 else 0x00
                segmentBytes[p++] = ((seg.speed ushr 8) and 0xFF).toByte()
                segmentBytes[p++] = (seg.speed and 0xFF).toByte()
                segmentBytes[p++] = ((seg.durationMs ushr 16) and 0xFF).toByte()
                segmentBytes[p++] = ((seg.durationMs ushr 8) and 0xFF).toByte()
                segmentBytes[p++] = (seg.durationMs and 0xFF).toByte()
            }
            val content = ByteArray(1 + segmentBytes.size)
            content[0] = (segmentBytes.size and 0xFF).toByte()
            System.arraycopy(segmentBytes, 0, content, 1, segmentBytes.size)
            WxFunctionCode.SEGMENT_DISPENSE to content
        }

        is WxPayload.CleanRequest -> WxFunctionCode.CLEAN to byteArrayOf(
            (payload.totalPin and 0xFF).toByte(),
            if (payload.totalForward) 0x01 else 0x00,
            ((payload.speed ushr 8) and 0xFF).toByte(),
            (payload.speed and 0xFF).toByte(),
            (payload.loopPin and 0xFF).toByte(),
            (payload.loopDirection and 0xFF).toByte(),
            ((payload.loopDurationMs ushr 16) and 0xFF).toByte(),
            ((payload.loopDurationMs ushr 8) and 0xFF).toByte(),
            (payload.loopDurationMs and 0xFF).toByte()
        )

        is WxPayload.Raw -> payload.functionCode to payload.content
        else -> throw IllegalArgumentException("payload is response-only: $payload")
    }

    fun decodePayload(frame: WxFrame): WxPayload {
        val c = frame.content
        return when (frame.functionCode) {
            WxFunctionCode.PIN_DISPENSE -> {
                if (c.size >= 5) {
                    WxPayload.PinDispenseRequest(
                        pin = c[0].toInt() and 0xFF,
                        forward = c[1].toInt() and 0xFF == 0x01,
                        speed = ((c[2].toInt() and 0xFF) shl 8) or (c[3].toInt() and 0xFF),
                        open = c[4].toInt() and 0xFF == 0x01
                    )
                } else if (c.size >= 2) {
                    WxPayload.PinDispenseResponse(
                        pin = c[0].toInt() and 0xFF,
                        status = c[1].toInt() and 0xFF
                    )
                } else {
                    WxPayload.Raw(frame.functionCode, c)
                }
            }

            WxFunctionCode.STOP_DISPENSE -> {
                if (c.isEmpty()) WxPayload.StopRequest
                else WxPayload.StopResponse(c[0].toInt() and 0xFF)
            }

            WxFunctionCode.SEGMENT_DISPENSE -> {
                if (c.size >= 1 && c.size == 1 + (c[0].toInt() and 0xFF) && (c[0].toInt() and 0xFF) % 7 == 0) {
                    // 请求：首字节为分段总长
                    val segments = ArrayList<DispenseSegment>()
                    var i = 1
                    while (i + 6 < c.size) {
                        segments += DispenseSegment(
                            pin = c[i].toInt() and 0xFF,
                            forward = c[i + 1].toInt() and 0xFF == 0x01,
                            speed = ((c[i + 2].toInt() and 0xFF) shl 8) or (c[i + 3].toInt() and 0xFF),
                            durationMs = ((c[i + 4].toInt() and 0xFF) shl 16) or
                                ((c[i + 5].toInt() and 0xFF) shl 8) or
                                (c[i + 6].toInt() and 0xFF)
                        )
                        i += 7
                    }
                    WxPayload.SegmentDispenseRequest(segments)
                } else if (c.size >= 2 && c.size % 2 == 0) {
                    val items = ArrayList<PinStatus>()
                    var i = 0
                    while (i + 1 < c.size) {
                        items += PinStatus(c[i].toInt() and 0xFF, c[i + 1].toInt() and 0xFF)
                        i += 2
                    }
                    WxPayload.SegmentDispenseResponse(items)
                } else {
                    WxPayload.Raw(frame.functionCode, c)
                }
            }

            WxFunctionCode.DISPENSE_FINISHED ->
                WxPayload.DispenseFinished(if (c.isNotEmpty()) c[0].toInt() and 0xFF else -1)

            WxFunctionCode.CLEAN -> {
                if (c.size >= 9) {
                    WxPayload.CleanRequest(
                        totalPin = c[0].toInt() and 0xFF,
                        totalForward = c[1].toInt() and 0xFF == 0x01,
                        speed = ((c[2].toInt() and 0xFF) shl 8) or (c[3].toInt() and 0xFF),
                        loopPin = c[4].toInt() and 0xFF,
                        loopDirection = c[5].toInt() and 0xFF,
                        loopDurationMs = ((c[6].toInt() and 0xFF) shl 16) or
                            ((c[7].toInt() and 0xFF) shl 8) or
                            (c[8].toInt() and 0xFF)
                    )
                } else if (c.size >= 2) {
                    WxPayload.CleanResponse(c[0].toInt() and 0xFF, c[1].toInt() and 0xFF)
                } else if (c.size == 1) {
                    // 示例响应只有 1 字节状态：70 01 00
                    WxPayload.CleanResponse(pin = 0, status = c[0].toInt() and 0xFF)
                } else {
                    WxPayload.Raw(frame.functionCode, c)
                }
            }

            WxFunctionCode.CLEAN_FINISHED ->
                WxPayload.CleanFinished(if (c.isNotEmpty()) c[0].toInt() and 0xFF else -1)

            else -> WxPayload.Raw(frame.functionCode, c)
        }
    }

    fun buildMessage(
        payload: WxPayload,
        address: Int = WxProtocol.DEFAULT_ADDRESS,
        screenCommand: Int = WxProtocol.DEFAULT_SCREEN,
        messageId: Int = 1
    ): ByteArray {
        val (func, content) = encodePayload(payload)
        return buildFrame(func, content, address, screenCommand, messageId)
    }

    fun parseMessage(raw: ByteArray): WxMessage {
        val frame = parseFrame(raw)
        return WxMessage(frame, decodePayload(frame))
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }

    fun fromHex(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace("\n", "").replace("\r", "")
        require(cleaned.length % 2 == 0) { "hex length must be even" }
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
