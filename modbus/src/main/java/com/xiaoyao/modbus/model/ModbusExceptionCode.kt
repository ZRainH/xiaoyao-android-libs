package com.xiaoyao.modbus.model

/**
 * Modbus 从站异常码（出现在异常响应第 3 字节）。
 * 异常响应功能码 = 原功能码 | 0x80。
 */
enum class ModbusExceptionCode(val code: Int, val description: String) {
    ILLEGAL_FUNCTION(0x01, "Illegal function"),
    ILLEGAL_DATA_ADDRESS(0x02, "Illegal data address"),
    ILLEGAL_DATA_VALUE(0x03, "Illegal data value"),
    SLAVE_DEVICE_FAILURE(0x04, "Slave device failure"),
    ACKNOWLEDGE(0x05, "Acknowledge"),
    SLAVE_DEVICE_BUSY(0x06, "Slave device busy"),
    MEMORY_PARITY_ERROR(0x08, "Memory parity error"),
    GATEWAY_PATH_UNAVAILABLE(0x0A, "Gateway path unavailable"),
    GATEWAY_TARGET_FAILED(0x0B, "Gateway target device failed to respond"),
    UNKNOWN(-1, "Unknown exception");

    companion object {
        /** 按协议异常码数值查找；未知则返回 [UNKNOWN] */
        fun from(code: Int): ModbusExceptionCode =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
