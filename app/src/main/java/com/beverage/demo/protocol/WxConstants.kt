package com.beverage.demo.protocol

/** 协议功能码 */
object WxFunctionCode {
    const val PIN_DISPENSE: Int = 0x20
    const val STOP_DISPENSE: Int = 0x22
    const val SEGMENT_DISPENSE: Int = 0x62
    const val DISPENSE_FINISHED: Int = 0x63
    const val CLEAN: Int = 0x70
    const val CLEAN_FINISHED: Int = 0x71
}

/** 协议层错误 / 状态码 */
object WxStatusCode {
    const val OK: Int = 0x00
    const val BUSY: Int = 0x0E
    const val NO_MATERIAL: Int = 0x10
    const val RUNNING: Int = 0x11
    const val ERROR: Int = 0x10 // 0x22 停止出料错误也用 0x10
}

object WxProtocol {
    val HEADER: ByteArray = byteArrayOf(0x57, 0x58) // "WX"
    const val HEADER_SIZE: Int = 8 // WX + addr + screen + msgId(2) + func + contentLen
    const val DEFAULT_ADDRESS: Int = 0x01
    const val DEFAULT_SCREEN: Int = 0x01
}
