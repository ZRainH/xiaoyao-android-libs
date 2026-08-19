package com.xiaoyao.modbus.model

import com.xiaoyao.modbus.model.ModbusFunctionCode as Fc

/**
 * 一笔主站事务的**期望应答上下文**。
 *
 * RTU 没有 TransactionId，靠本对象描述“这次请求期待什么样的应答”，
 * 供拆帧（软匹配）与校验（严格匹配）使用，从而实现请求/响应一一对应。
 *
 * @property slaveId 期望从站地址；0 表示不校验从站（少用）
 * @property functionCode 期望功能码（正常应答）；异常应答为 `functionCode | 0x80`
 * @property address 请求起始地址（写回显校验用）
 * @property quantity 读取数量，或写多线圈/寄存器数量；单写时为 1
 * @property coilOn 仅 05 使用：期望线圈 ON/OFF
 * @property registerValue 仅 06 使用：期望写入的寄存器值
 */
data class ModbusRequestContext(
    val slaveId: Int,
    val functionCode: Int,
    /** 请求起始地址（读/写） */
    val address: Int,
    /** 读数量，或写多线圈/寄存器数量；单写时为 1 */
    val quantity: Int = 1,
    /** 05 单写线圈期望值：true=0xFF00，false=0x0000；其它功能码忽略 */
    val coilOn: Boolean? = null,
    /** 06 单写寄存器期望值；其它功能码忽略 */
    val registerValue: Int? = null
) {
    /** 从站异常应答时的功能码 = 原功能码最高位置 1 */
    val exceptionFunctionCode: Int get() = functionCode or 0x80

    companion object {
        /** 构造读线圈/离散量上下文（01/02） */
        fun readBits(slaveId: Int, functionCode: Int, address: Int, quantity: Int) =
            ModbusRequestContext(slaveId, functionCode, address, quantity)

        /** 构造读寄存器上下文（03/04） */
        fun readRegisters(slaveId: Int, functionCode: Int, address: Int, quantity: Int) =
            ModbusRequestContext(slaveId, functionCode, address, quantity)

        /** 构造写单线圈上下文（05） */
        fun writeSingleCoil(slaveId: Int, address: Int, on: Boolean) =
            ModbusRequestContext(
                slaveId = slaveId,
                functionCode = Fc.WRITE_SINGLE_COIL,
                address = address,
                quantity = 1,
                coilOn = on
            )

        /** 构造写单寄存器上下文（06） */
        fun writeSingleRegister(slaveId: Int, address: Int, value: Int) =
            ModbusRequestContext(
                slaveId = slaveId,
                functionCode = Fc.WRITE_SINGLE_REGISTER,
                address = address,
                quantity = 1,
                registerValue = value and 0xFFFF
            )

        /** 构造写多线圈上下文（15） */
        fun writeMultipleCoils(slaveId: Int, address: Int, quantity: Int) =
            ModbusRequestContext(
                slaveId = slaveId,
                functionCode = Fc.WRITE_MULTIPLE_COILS,
                address = address,
                quantity = quantity
            )

        /** 构造写多寄存器上下文（16） */
        fun writeMultipleRegisters(slaveId: Int, address: Int, quantity: Int) =
            ModbusRequestContext(
                slaveId = slaveId,
                functionCode = Fc.WRITE_MULTIPLE_REGISTERS,
                address = address,
                quantity = quantity
            )
    }
}
