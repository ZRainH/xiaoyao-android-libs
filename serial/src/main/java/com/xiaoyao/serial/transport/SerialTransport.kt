package com.xiaoyao.serial.transport

/**
 * 底层字节通道抽象。
 *
 * 内置实现：[NativeSerialTransport]（`com.licheedev:android-serialport`）；
 * 也可自行实现蓝牙 / TCP 等其它通道。
 */
interface SerialTransport {
    /** 通道标识，便于日志 */
    val name: String

    fun isOpen(): Boolean

    /**
     * 写入数据。
     * @throws Exception 写入失败
     */
    fun write(data: ByteArray, timeoutMs: Int)

    /**
     * 阻塞读取。
     * @return 实际读到的字节数；超时返回 0
     * @throws Exception 通道异常（断开等）
     */
    fun read(buffer: ByteArray, timeoutMs: Int): Int

    fun close()
}
