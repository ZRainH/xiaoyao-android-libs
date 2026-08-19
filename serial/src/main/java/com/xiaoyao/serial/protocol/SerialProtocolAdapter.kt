package com.xiaoyao.serial.protocol

/**
 * 串口上层协议适配器。
 *
 * 原始收发仍走 [com.xiaoyao.serial.connection.SerialConnection.incomingBytes] /
 * [com.xiaoyao.serial.connection.SerialConnection.write]；
 * 本接口负责「业务载荷 ↔ 线路字节」的组帧 / 拆帧，便于同一套连接复用多种协议。
 */
interface SerialProtocolAdapter {
    /** 适配器名称，便于日志与多连接区分 */
    val name: String

    /**
     * 将业务载荷编码为可直接写入串口的字节。
     * 默认原样透传。
     */
    fun encode(payload: ByteArray): ByteArray = payload

    /**
     * 喂入原始接收字节，返回已完整的业务帧列表（可能为空）。
     * 实现类自行维护半包缓冲。
     */
    fun feed(chunk: ByteArray): List<ByteArray>

    /** 清空内部半包缓冲（重连或协议切换时调用） */
    fun reset()
}
