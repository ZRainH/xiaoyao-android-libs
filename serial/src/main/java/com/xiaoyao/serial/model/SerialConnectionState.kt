package com.xiaoyao.serial.model

/**
 * 串口连接状态。
 */
enum class SerialConnectionState {
    /** 未连接 / 已关闭 */
    DISCONNECTED,
    /** 正在打开 */
    CONNECTING,
    /** 已连接 */
    CONNECTED,
    /** 读写异常导致失败 */
    ERROR
}
