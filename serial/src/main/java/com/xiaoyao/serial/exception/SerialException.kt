package com.xiaoyao.serial.exception

/**
 * 串口异常。
 */
class SerialException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
