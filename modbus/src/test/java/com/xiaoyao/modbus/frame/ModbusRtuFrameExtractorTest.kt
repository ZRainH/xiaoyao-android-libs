package com.xiaoyao.modbus.frame

import com.xiaoyao.modbus.crc.ModbusCrc
import com.xiaoyao.modbus.model.ModbusFunctionCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModbusRtuFrameExtractorTest {

    @Test
    fun `expected length for read holding registers`() {
        // addr=1, func=03, byteCount=4, data=4, crc=2 => 9
        val prefix = byteArrayOf(0x01, 0x03, 0x04)
        assertEquals(9, ModbusRtuFrameExtractor.expectedLength(prefix))
    }

    @Test
    fun `expected length for write single register echo`() {
        val prefix = byteArrayOf(0x01, 0x06, 0x00, 0x00)
        assertEquals(8, ModbusRtuFrameExtractor.expectedLength(prefix))
    }

    @Test
    fun `expected length for exception is 5`() {
        val prefix = byteArrayOf(0x01, 0x83.toByte(), 0x02)
        assertEquals(5, ModbusRtuFrameExtractor.expectedLength(prefix))
    }

    @Test
    fun `extract normal frame from exact buffer`() {
        val body = byteArrayOf(0x01, 0x03, 0x02, 0x00, 0x00)
        val frame = ModbusCrc.append(body)
        val result = ModbusRtuFrameExtractor.tryExtract(frame, expectedSlaveId = 1, expectedFunction = 0x03)
        assertNotNull(result.frame)
        assertArrayEquals(frame, result.frame)
        assertEquals(frame.size, result.consumed)
    }

    @Test
    fun `extract frame when sticky with garbage prefix`() {
        val body = byteArrayOf(0x01, 0x03, 0x02, 0x12, 0x34)
        val frame = ModbusCrc.append(body)
        val sticky = byteArrayOf(0x00, 0xFF.toByte()) + frame
        val result = ModbusRtuFrameExtractor.tryExtract(sticky, 1, ModbusFunctionCode.READ_HOLDING_REGISTERS)
        assertNotNull(result.frame)
        assertArrayEquals(frame, result.frame)
        assertEquals(sticky.size, result.consumed)
    }

    @Test
    fun `extract first frame when two responses sticky`() {
        val frame1 = ModbusCrc.append(byteArrayOf(0x01, 0x03, 0x02, 0x00, 0x01))
        val frame2 = ModbusCrc.append(byteArrayOf(0x01, 0x03, 0x02, 0x00, 0x02))
        val sticky = frame1 + frame2
        val result = ModbusRtuFrameExtractor.tryExtract(sticky, 1, 0x03)
        assertNotNull(result.frame)
        assertArrayEquals(frame1, result.frame)
        assertEquals(frame1.size, result.consumed)

        val remain = sticky.copyOfRange(result.consumed, sticky.size)
        val second = ModbusRtuFrameExtractor.tryExtract(remain, 1, 0x03)
        assertArrayEquals(frame2, second.frame)
    }

    @Test
    fun `half packet needs more data`() {
        val body = byteArrayOf(0x01, 0x03, 0x02, 0x00, 0x00)
        val frame = ModbusCrc.append(body)
        val half = frame.copyOf(frame.size - 2)
        val result = ModbusRtuFrameExtractor.tryExtract(half, 1, 0x03)
        assertNull(result.frame)
        assertTrue(result.needMore)
        assertEquals(0, result.consumed)
    }

    @Test
    fun `ignore frame for other slave id and keep sliding`() {
        val other = ModbusCrc.append(byteArrayOf(0x02, 0x03, 0x02, 0x00, 0x00))
        val mine = ModbusCrc.append(byteArrayOf(0x01, 0x03, 0x02, 0x00, 0x11))
        val sticky = other + mine
        val result = ModbusRtuFrameExtractor.tryExtract(sticky, expectedSlaveId = 1, expectedFunction = 0x03)
        assertNotNull(result.frame)
        assertArrayEquals(mine, result.frame)
    }
}
