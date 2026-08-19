package com.xiaoyao.modbus.model

/**
 * 常用 Modbus 功能码常量。
 *
 * | 码 | 含义 |
 * |----|------|
 * | 01 | 读线圈 |
 * | 02 | 读离散量输入 |
 * | 03 | 读保持寄存器 |
 * | 04 | 读输入寄存器 |
 * | 05 | 写单个线圈 |
 * | 06 | 写单个寄存器 |
 * | 15 | 写多个线圈 |
 * | 16 | 写多个寄存器 |
 */
object ModbusFunctionCode {
    const val READ_COILS = 0x01
    const val READ_DISCRETE_INPUTS = 0x02
    const val READ_HOLDING_REGISTERS = 0x03
    const val READ_INPUT_REGISTERS = 0x04
    const val WRITE_SINGLE_COIL = 0x05
    const val WRITE_SINGLE_REGISTER = 0x06
    const val WRITE_MULTIPLE_COILS = 0x0F
    const val WRITE_MULTIPLE_REGISTERS = 0x10
}
