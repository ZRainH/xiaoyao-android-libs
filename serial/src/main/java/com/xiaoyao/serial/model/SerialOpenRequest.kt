package com.xiaoyao.serial.model

import com.xiaoyao.serial.protocol.RawProtocolAdapter
import com.xiaoyao.serial.protocol.SerialProtocolAdapter
import com.xiaoyao.serial.transport.SerialTransport

/**
 * 打开一路连接的请求。
 *
 * [protocol] 有状态（内部缓冲），**每路连接请使用独立实例**；
 * [RawProtocolAdapter] 无状态可共享。
 */
data class SerialOpenRequest(
    /** 业务侧连接 ID，多路管理用 */
    val connectionId: String,
    /** 已打开的底层通道 */
    val transport: SerialTransport,
    val config: SerialConfig = SerialConfig(),
    val protocol: SerialProtocolAdapter = RawProtocolAdapter
)
