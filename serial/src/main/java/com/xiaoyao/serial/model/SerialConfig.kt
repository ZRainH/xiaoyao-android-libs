package com.xiaoyao.serial.model

/**
 * 串口参数。
 */
data class SerialConfig(
    val path: String = "",
    val baudRate: Int = 9600,
    val dataBits: DataBits = DataBits.BITS_8,
    val stopBits: StopBits = StopBits.BITS_1,
    val parity: Parity = Parity.NONE,
    /** 单次读等待；`<= 0` 表示阻塞直到有数据或关闭 */
    val readTimeoutMs: Int = 200,
    val writeTimeoutMs: Int = 2000,
    val executeTimeoutMs: Long = 1000L,
    /**
     * true：同一时刻只允许一笔 execute（如 Modbus RTU）。
     * false：可并发，靠 matcher 区分（如带 messageId 的协议）。
     */
    val serializeExecute: Boolean = false
)

enum class DataBits(val value: Int) {
    BITS_5(5),
    BITS_6(6),
    BITS_7(7),
    BITS_8(8)
}

enum class StopBits(val value: Int) {
    BITS_1(1),
    BITS_2(2)
}

enum class Parity(val value: Int) {
    NONE(0),
    ODD(1),
    EVEN(2)
}
