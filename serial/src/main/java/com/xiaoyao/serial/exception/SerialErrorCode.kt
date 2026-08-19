package com.xiaoyao.serial.exception

/**
 * 串口错误码。
 */
object SerialErrorCode {
    const val NOT_CONNECTED = "NOT_CONNECTED"
    const val WRITE_FAILED = "WRITE_FAILED"
    const val READ_FAILED = "READ_FAILED"
    const val OPEN_FAILED = "OPEN_FAILED"
    const val ALREADY_OPEN = "ALREADY_OPEN"
    const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
    /** 请求-应答事务超时 */
    const val TIMEOUT = "TIMEOUT"
    const val UNKNOWN = "UNKNOWN"
}
