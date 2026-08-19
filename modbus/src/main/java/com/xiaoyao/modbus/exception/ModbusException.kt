package com.xiaoyao.modbus.exception

import com.xiaoyao.modbus.model.ModbusExceptionCode

/**
 * Modbus 库统一异常。
 *
 * @property code 库内错误码字符串，见伴生常量
 * @property exceptionCode 若为从站异常响应，对应协议异常码；否则为 null
 */
class ModbusException(
    val code: String,
    message: String,
    val exceptionCode: ModbusExceptionCode? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    companion object {
        /** CRC 校验失败 */
        const val CRC_ERROR = "CRC_ERROR"
        /** 等待响应超时 */
        const val TIMEOUT = "TIMEOUT"
        /** 响应字段与请求不对应 / 帧非法 */
        const val INVALID_RESPONSE = "INVALID_RESPONSE"
        /** 从站返回异常功能码（原功能码 \| 0x80） */
        const val EXCEPTION_RESPONSE = "EXCEPTION_RESPONSE"
        /** 调用参数非法 */
        const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
        /** 串口写入/连接等传输层错误 */
        const val TRANSPORT_ERROR = "TRANSPORT_ERROR"
        /** 写后读回校验不一致 */
        const val VERIFY_MISMATCH = "VERIFY_MISMATCH"
    }
}
