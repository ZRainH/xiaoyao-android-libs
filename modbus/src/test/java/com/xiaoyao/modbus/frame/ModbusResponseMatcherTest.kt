package com.xiaoyao.modbus.frame

import com.xiaoyao.modbus.crc.ModbusCrc
import com.xiaoyao.modbus.exception.ModbusException
import com.xiaoyao.modbus.model.ModbusFunctionCode
import com.xiaoyao.modbus.model.ModbusRequestContext
import org.junit.Assert.assertThrows
import org.junit.Test

class ModbusResponseMatcherTest {

    @Test
    fun `read registers matches byteCount to quantity`() {
        val ctx = ModbusRequestContext.readRegisters(1, ModbusFunctionCode.READ_HOLDING_REGISTERS, 0, 2)
        val frame = ModbusCrc.append(byteArrayOf(0x01, 0x03, 0x04, 0x00, 0x01, 0x00, 0x02))
        ModbusResponseMatcher.assertMatches(frame, ctx)
    }

    @Test
    fun `read registers rejects wrong byteCount`() {
        val ctx = ModbusRequestContext.readRegisters(1, ModbusFunctionCode.READ_HOLDING_REGISTERS, 0, 2)
        // byteCount=2 but quantity expects 4
        val frame = ModbusCrc.append(byteArrayOf(0x01, 0x03, 0x02, 0x00, 0x01))
        assertThrows(ModbusException::class.java) {
            ModbusResponseMatcher.assertMatches(frame, ctx)
        }
    }

    @Test
    fun `write single coil echo must match`() {
        val ctx = ModbusRequestContext.writeSingleCoil(1, 0, true)
        val ok = ModbusCrc.append(byteArrayOf(0x01, 0x05, 0x00, 0x00, 0xFF.toByte(), 0x00))
        ModbusResponseMatcher.assertMatches(ok, ctx)

        val bad = ModbusCrc.append(byteArrayOf(0x01, 0x05, 0x00, 0x00, 0x00, 0x00))
        assertThrows(ModbusException::class.java) {
            ModbusResponseMatcher.assertMatches(bad, ctx)
        }
    }

    @Test
    fun `wrong slave is rejected`() {
        val ctx = ModbusRequestContext.readRegisters(1, 0x03, 0, 1)
        val frame = ModbusCrc.append(byteArrayOf(0x02, 0x03, 0x02, 0x00, 0x00))
        assertThrows(ModbusException::class.java) {
            ModbusResponseMatcher.assertMatches(frame, ctx)
        }
    }

    @Test
    fun `exception response is not treated as success`() {
        val ctx = ModbusRequestContext.writeSingleRegister(1, 10, 0x1234)
        // 功能码 0x86 = 06|0x80，非法数据地址
        val frame = ModbusCrc.append(byteArrayOf(0x01, 0x86.toByte(), 0x02))
        val ex = assertThrows(ModbusException::class.java) {
            ModbusResponseMatcher.assertMatches(frame, ctx)
        }
        assert(ex.code == ModbusException.EXCEPTION_RESPONSE)
    }
}
