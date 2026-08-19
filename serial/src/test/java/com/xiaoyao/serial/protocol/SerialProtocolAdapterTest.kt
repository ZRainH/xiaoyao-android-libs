package com.xiaoyao.serial.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialProtocolAdapterTest {

    @Test
    fun `delimiter splits sticky frames`() {
        val adapter = DelimiterProtocolAdapter.line()
        val frames = adapter.feed("AB\nCD\nEF".toByteArray())
        assertEquals(2, frames.size)
        assertArrayEquals("AB".toByteArray(), frames[0])
        assertArrayEquals("CD".toByteArray(), frames[1])

        val rest = adapter.feed("G\n".toByteArray())
        assertEquals(1, rest.size)
        assertArrayEquals("EFG".toByteArray(), rest[0])
    }

    @Test
    fun `delimiter encode appends separator`() {
        val adapter = DelimiterProtocolAdapter.crlf()
        val wire = adapter.encode("PING".toByteArray())
        assertArrayEquals("PING\r\n".toByteArray(), wire)
    }

    @Test
    fun `length field extracts complete frames`() {
        val adapter = LengthFieldProtocolAdapter(
            lengthOffset = 0,
            lengthSize = 1,
            lengthIncludesHeader = false
        )
        // len=2, payload AA BB ; len=1, payload CC
        val frames = adapter.feed(byteArrayOf(0x02, 0xAA.toByte(), 0xBB.toByte(), 0x01, 0xCC.toByte()))
        assertEquals(2, frames.size)
        assertArrayEquals(byteArrayOf(0x02, 0xAA.toByte(), 0xBB.toByte()), frames[0])
        assertArrayEquals(byteArrayOf(0x01, 0xCC.toByte()), frames[1])
    }

    @Test
    fun `raw adapter passes chunk through`() {
        val chunk = byteArrayOf(1, 2, 3)
        val frames = RawProtocolAdapter.feed(chunk)
        assertEquals(1, frames.size)
        assertArrayEquals(chunk, frames[0])
        assertTrue(RawProtocolAdapter.feed(byteArrayOf()).isEmpty())
    }

    @Test
    fun `delimiter resyncs after oversized garbage`() {
        val adapter = DelimiterProtocolAdapter(
            delimiter = byteArrayOf('\n'.code.toByte()),
            maxFrameSize = 4
        )
        // 超长噪声触发溢出重置，随后合法帧仍可解析
        val frames = adapter.feed("XXXXX\nOK\n".toByteArray())
        assertEquals(1, frames.size)
        assertArrayEquals("OK".toByteArray(), frames[0])
    }

    @Test
    fun `delimiter accepts frame at max size then continues`() {
        val adapter = DelimiterProtocolAdapter(
            delimiter = byteArrayOf('\n'.code.toByte()),
            maxFrameSize = 3
        )
        val frames = adapter.feed("ABC\nD\n".toByteArray())
        assertEquals(2, frames.size)
        assertArrayEquals("ABC".toByteArray(), frames[0])
        assertArrayEquals("D".toByteArray(), frames[1])
    }

    @Test
    fun `delimiter accepts payload equal to maxFrameSize with crlf`() {
        val adapter = DelimiterProtocolAdapter(
            delimiter = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte()),
            includeDelimiter = false,
            maxFrameSize = 10
        )
        val payload = "0123456789"
        val frames = adapter.feed("$payload\r\n".toByteArray())
        assertEquals(1, frames.size)
        assertArrayEquals(payload.toByteArray(), frames[0])
    }

    @Test
    fun `length field rejects undersized total and resyncs`() {
        val adapter = LengthFieldProtocolAdapter(
            lengthOffset = 0,
            lengthSize = 2,
            lengthIncludesHeader = true,
            maxFrameSize = 16
        )
        // 00 01 -> total=1 < minLen=2，丢弃重同步；随后 00 03 99 合法
        val frames = adapter.feed(byteArrayOf(0x00, 0x01, 0x00, 0x03, 0x99.toByte()))
        assertEquals(1, frames.size)
        assertArrayEquals(byteArrayOf(0x00, 0x03, 0x99.toByte()), frames[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `length field requires lengthOffset equals header size`() {
        LengthFieldProtocolAdapter(
            lengthOffset = 3,
            lengthSize = 1,
            header = byteArrayOf(0xAA.toByte(), 0x55)
        )
    }

    @Test
    fun `custom adapter can be composed`() {
        val adapter = object : SerialProtocolAdapter {
            override val name = "xor-demo"
            private val buf = ArrayList<Byte>()
            override fun encode(payload: ByteArray): ByteArray =
                payload.map { (it.toInt() xor 0x55).toByte() }.toByteArray()

            override fun feed(chunk: ByteArray): List<ByteArray> {
                chunk.forEach { buf.add((it.toInt() xor 0x55).toByte()) }
                if (buf.size < 2) return emptyList()
                val out = ByteArray(buf.size) { i -> buf[i] }
                buf.clear()
                return listOf(out)
            }

            override fun reset() {
                buf.clear()
            }
        }
        val encoded = adapter.encode(byteArrayOf(0x00, 0x01))
        assertArrayEquals(byteArrayOf(0x55, 0x54), encoded)
        val decoded = adapter.feed(encoded)
        assertEquals(1, decoded.size)
        assertArrayEquals(byteArrayOf(0x00, 0x01), decoded[0])
    }
}
