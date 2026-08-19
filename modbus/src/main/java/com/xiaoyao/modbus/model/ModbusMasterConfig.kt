package com.xiaoyao.modbus.model

/**
 * 主站可靠性增强配置。
 *
 * RTU 无 TransactionId，除字段匹配外，通过重试、帧间隔、写后读回进一步降低误匹配风险。
 */
data class ModbusMasterConfig(
    /**
     * 单笔事务失败后的额外重试次数（不含首次）。
     * 仅对超时 / CRC / 字段不匹配 / 传输错误重试；从站异常码默认不重试。
     */
    val maxRetries: Int = 2,
    /** 每次重试前等待（毫秒） */
    val retryDelayMs: Long = 50L,
    /**
     * 发请求前静默等待（毫秒）。
     * drain 之后再等一会，让总线上残留字节被采集并清掉。
     */
    val preSendSettleMs: Long = 20L,
    /**
     * 收齐合法应答后的帧间隔等待（毫秒）。
     * 用于丢弃应答后可能粘上的多余字节，再进入下一笔事务。
     */
    val postReceiveSettleMs: Long = 20L,
    /**
     * 写成功后是否再读回校验。
     * - 05 → 再用 01 读该线圈
     * - 06 → 再用 03 读该寄存器
     * - 15/16 → 按写入范围读回比较
     */
    val verifyWrites: Boolean = true,
    /** 写后读回校验失败时是否自动重试整笔写（默认关：避免已写入成功却因读抖动而重复写） */
    val retryOnVerifyMismatch: Boolean = false
) {
    companion object {
        /** 默认增强配置 */
        val DEFAULT = ModbusMasterConfig()

        /** 更快、不写后校验（总线很干净时可调） */
        val FAST = ModbusMasterConfig(
            maxRetries = 1,
            retryDelayMs = 20L,
            preSendSettleMs = 5L,
            postReceiveSettleMs = 5L,
            verifyWrites = false
        )

        /**
         * 极速模式（高频轮询专用）。
         * 关闭所有强制静默延时，关闭写后校验，适合信道干净、需极限吞吐量的场景。
         */
        val HIGH_SPEED = ModbusMasterConfig(
            maxRetries = 1,
            retryDelayMs = 10L,
            preSendSettleMs = 0L,
            postReceiveSettleMs = 0L,
            verifyWrites = false
        )
    }
}
