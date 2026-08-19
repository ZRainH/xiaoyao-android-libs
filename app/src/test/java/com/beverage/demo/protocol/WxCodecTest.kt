package com.beverage.demo.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WxCodecTest {

    @Test
    fun `stop request matches document example`() {
        val frame = WxCodec.buildMessage(WxPayload.StopRequest, messageId = 1)
        assertEquals(
            "57 58 01 01 00 01 22 00 D4",
            WxCodec.toHex(frame)
        )
    }

    @Test
    fun `stop response parses`() {
        val raw = WxCodec.fromHex("57 58 01 01 00 01 22 01 00 D5")
        val msg = WxCodec.parseMessage(raw)
        val payload = msg.payload as WxPayload.StopResponse
        assertEquals(0x00, payload.status)
    }

    @Test
    fun `segment single matches document example`() {
        val frame = WxCodec.buildMessage(
            WxPayload.SegmentDispenseRequest(
                listOf(
                    DispenseSegment(
                        pin = 1,
                        forward = true,
                        speed = 0x3232,
                        durationMs = 0x00093F
                    )
                )
            ),
            messageId = 1
        )
        assertEquals(
            "57 58 01 01 00 01 62 08 07 01 01 32 32 00 09 3F D1",
            WxCodec.toHex(frame)
        )
    }

    @Test
    fun `segment multi matches document example`() {
        val frame = WxCodec.buildMessage(
            WxPayload.SegmentDispenseRequest(
                listOf(
                    DispenseSegment(2, true, 0x3232, 0x000172),
                    DispenseSegment(3, true, 0x3232, 0x00023E),
                    DispenseSegment(6, true, 0x3232, 0x001AC0)
                )
            ),
            messageId = 1
        )
        assertEquals(
            "57 58 01 01 00 01 62 16 15 02 01 32 32 00 01 72 03 01 32 32 00 02 3E 06 01 32 32 00 1A C0 06",
            WxCodec.toHex(frame)
        )
    }

    @Test
    fun `clean request matches document example`() {
        val frame = WxCodec.buildMessage(
            WxPayload.CleanRequest(
                totalPin = 0x21,
                totalForward = true,
                speed = 0x3232,
                loopPin = 0x06,
                loopDirection = 0x01,
                loopDurationMs = 0x002710
            ),
            messageId = 1
        )
        assertEquals(
            "57 58 01 01 00 01 70 09 21 01 32 32 06 01 00 27 10 EF",
            WxCodec.toHex(frame)
        )
    }

    @Test
    fun `adapter extracts sticky frames`() {
        val adapter = WxProtocolAdapter()
        val a = WxCodec.fromHex("57 58 01 01 00 01 22 01 00 D5")
        val b = WxCodec.fromHex("57 58 01 01 00 01 63 01 00 16")
        val sticky = a + byteArrayOf(0x00, 0x11) + b
        val frames = adapter.feed(sticky)
        assertEquals(2, frames.size)
        assertTrue(WxChecksum.verify(frames[0]))
        assertTrue(WxChecksum.verify(frames[1]))
        assertArrayEquals(a, frames[0])
        assertArrayEquals(b, frames[1])
    }
}
