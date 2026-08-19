package com.xiaoyao.modbus.crc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModbusCrcTest {

    @Test
    fun `crc matches common read holding registers example`() {
        // 01 03 00 00 00 01 -> CRC 84 0A
        val body = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x01)
        val frame = ModbusCrc.append(body)
        assertEquals(8, frame.size)
        assertEquals(0x84.toByte(), frame[6])
        assertEquals(0x0A.toByte(), frame[7])
        assertTrue(ModbusCrc.verify(frame))
    }

    @Test
    fun `crc matches write single coil on example`() {
        // 01 05 00 00 FF 00 -> CRC 8C 3A
        val body = byteArrayOf(0x01, 0x05, 0x00, 0x00, 0xFF.toByte(), 0x00)
        val frame = ModbusCrc.append(body)
        assertEquals(0x8C.toByte(), frame[6])
        assertEquals(0x3A.toByte(), frame[7])
    }
}
