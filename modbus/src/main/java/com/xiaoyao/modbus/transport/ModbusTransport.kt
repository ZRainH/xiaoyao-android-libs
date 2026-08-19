package com.xiaoyao.modbus.transport

import com.xiaoyao.modbus.model.ModbusRequestContext

/**
 * Modbus 传输层抽象。
 *
 * 实现类负责：发送请求字节、收取完整响应帧，并保证该帧与 [context] 一一对应。
 * 默认实现见 [SerialModbusTransport]。
 */
interface ModbusTransport {
    /**
     * @param request 已含 CRC 的完整请求帧
     * @param context 本次事务期望应答描述（从站/功能码/地址/数量等）
     * @param timeoutMs 总超时
     * @return 与请求对应的完整响应帧（含 CRC）
     */
    suspend fun transact(
        request: ByteArray,
        context: ModbusRequestContext,
        timeoutMs: Long
    ): ByteArray
}
