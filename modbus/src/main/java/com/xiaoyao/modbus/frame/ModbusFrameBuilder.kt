package com.xiaoyao.modbus.frame

import com.xiaoyao.modbus.crc.ModbusCrc
import com.xiaoyao.modbus.model.ModbusFunctionCode

/**
 * Modbus RTU **请求组帧**。
 *
 * 帧布局：`[地址 1B][功能码 1B][数据区 NB][CRC 2B]`
 * - 数据区中的地址/数量/寄存器值为 **大端**
 * - CRC 为 **小端**（低字节在前），由 [ModbusCrc.append] 追加
 */
object ModbusFrameBuilder {

    /**
     * 组装完整请求帧并附加 CRC。
     *
     * @param slaveId 0=广播，1–247=从站
     * @param functionCode 功能码
     * @param pduData 功能码后的数据区（不含地址/功能码/CRC）
     */
    fun build(slaveId: Int, functionCode: Int, pduData: ByteArray = byteArrayOf()): ByteArray {
        require(slaveId in 0..247) { "slaveId must be 0..247" }
        val body = ByteArray(2 + pduData.size)
        body[0] = slaveId.toByte()
        body[1] = functionCode.toByte()
        if (pduData.isNotEmpty()) {
            System.arraycopy(pduData, 0, body, 2, pduData.size)
        }
        return ModbusCrc.append(body)
    }

    /**
     * 读位类请求（01/02）：起始地址 + 数量。
     * 协议限制 quantity ∈ [1, 2000]。
     */
    fun readBits(slaveId: Int, functionCode: Int, address: Int, quantity: Int): ByteArray {
        require(quantity in 1..2000) { "quantity must be 1..2000" }
        return build(slaveId, functionCode, u16(address) + u16(quantity))
    }

    /**
     * 读寄存器类请求（03/04）：起始地址 + 数量。
     * 协议限制 quantity ∈ [1, 125]。
     */
    fun readRegisters(slaveId: Int, functionCode: Int, address: Int, quantity: Int): ByteArray {
        require(quantity in 1..125) { "quantity must be 1..125" }
        return build(slaveId, functionCode, u16(address) + u16(quantity))
    }

    /**
     * 写单个线圈（05）。
     * ON = `FF 00`，OFF = `00 00`。
     */
    fun writeSingleCoil(slaveId: Int, address: Int, on: Boolean): ByteArray {
        val value = if (on) byteArrayOf(0xFF.toByte(), 0x00) else byteArrayOf(0x00, 0x00)
        return build(slaveId, ModbusFunctionCode.WRITE_SINGLE_COIL, u16(address) + value)
    }

    /** 写单个寄存器（06） */
    fun writeSingleRegister(slaveId: Int, address: Int, value: Int): ByteArray {
        require(value in 0..0xFFFF) { "value must be 0..65535" }
        return build(slaveId, ModbusFunctionCode.WRITE_SINGLE_REGISTER, u16(address) + u16(value))
    }

    /**
     * 写多个线圈（15）。
     * 线圈状态按 bit 打包，**字节内低位对应靠前的线圈**。
     */
    fun writeMultipleCoils(slaveId: Int, address: Int, values: BooleanArray): ByteArray {
        require(values.isNotEmpty() && values.size <= 1968) { "coil count must be 1..1968" }
        val byteCount = (values.size + 7) / 8
        val coilBytes = ByteArray(byteCount)
        for (i in values.indices) {
            if (values[i]) {
                coilBytes[i / 8] = (coilBytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
            }
        }
        val data = u16(address) + u16(values.size) + byteArrayOf(byteCount.toByte()) + coilBytes
        return build(slaveId, ModbusFunctionCode.WRITE_MULTIPLE_COILS, data)
    }

    /**
     * 写多个寄存器（16）。
     * 每个寄存器 2 字节大端依次拼接。
     */
    fun writeMultipleRegisters(slaveId: Int, address: Int, values: IntArray): ByteArray {
        require(values.isNotEmpty() && values.size <= 123) { "register count must be 1..123" }
        val payload = ByteArray(values.size * 2)
        for (i in values.indices) {
            require(values[i] in 0..0xFFFF) { "value[$i] must be 0..65535" }
            payload[i * 2] = ((values[i] shr 8) and 0xFF).toByte()
            payload[i * 2 + 1] = (values[i] and 0xFF).toByte()
        }
        val data = u16(address) + u16(values.size) + byteArrayOf(payload.size.toByte()) + payload
        return build(slaveId, ModbusFunctionCode.WRITE_MULTIPLE_REGISTERS, data)
    }

    /** 无符号 16 位大端编码 */
    private fun u16(value: Int): ByteArray = byteArrayOf(
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
