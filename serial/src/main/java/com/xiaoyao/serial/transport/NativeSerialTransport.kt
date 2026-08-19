package com.xiaoyao.serial.transport

import android.serialport.SerialPort
import com.xiaoyao.serial.exception.SerialErrorCode
import com.xiaoyao.serial.exception.SerialException
import com.xiaoyao.serial.model.SerialConfig
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于 [com.licheedev:android-serialport] 的原生串口传输实现。
 */
class NativeSerialTransport private constructor(
    private val path: String,
    private val serialPort: SerialPort
) : SerialTransport {

    private val closed = AtomicBoolean(false)

    private val input: InputStream = serialPort.inputStream
    private val output: OutputStream = serialPort.outputStream
    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "serial-write-$path").apply { isDaemon = true }
    }

    override val name: String get() = path

    override fun isOpen(): Boolean = !closed.get()

    override fun write(data: ByteArray, timeoutMs: Int) {
        ensureOpen()
        if (timeoutMs <= 0) {
            writeFully(data)
            return
        }
        val future = writeExecutor.submit<Unit> { writeFully(data) }
        try {
            future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            // JNI write 可能无法被中断：关闭通道，避免上层重试造成重复发包 / 假失败
            closeQuietly()
            throw SerialException(
                SerialErrorCode.WRITE_FAILED,
                "write timeout ${timeoutMs}ms: $path"
            )
        } catch (e: ExecutionException) {
            val cause = e.cause
            closeQuietly()
            if (cause is SerialException) throw cause
            throw SerialException(
                SerialErrorCode.WRITE_FAILED,
                cause?.message ?: "write failed",
                cause
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            closeQuietly()
            throw SerialException(SerialErrorCode.WRITE_FAILED, "write interrupted: $path", e)
        }
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        ensureOpen()
        // timeoutMs <= 0：阻塞直到有数据或通道关闭（避免 0 被当成“立刻超时”导致读循环空转打满 CPU）
        val blockForever = timeoutMs <= 0
        val deadline = if (blockForever) Long.MAX_VALUE else System.currentTimeMillis() + timeoutMs
        try {
            while (true) {
                if (closed.get()) {
                    throw SerialException(SerialErrorCode.NOT_CONNECTED, "serial closed: $path")
                }
                val available = input.available()
                if (available > 0) {
                    val toRead = minOf(available, buffer.size)
                    val n = input.read(buffer, 0, toRead)
                    if (n < 0) throw IOException("serial eof: $path")
                    return n
                }
                if (!blockForever && System.currentTimeMillis() >= deadline) return 0
                Thread.sleep(READ_POLL_MS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return 0
        } catch (e: SerialException) {
            throw e
        } catch (e: IOException) {
            closeQuietly()
            throw SerialException(SerialErrorCode.READ_FAILED, e.message ?: "read failed", e)
        }
    }

    override fun close() {
        closeQuietly()
    }

    private fun closeQuietly() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { serialPort.tryClose() }
        // 写超时后 native write 可能仍挂起：shutdownNow + 短暂等待，尽量回收写线程；
        // 极端驱动卡死时该 daemon 线程可能残留至进程退出（单连接单池，影响有限）。
        writeExecutor.shutdownNow()
        runCatching { writeExecutor.awaitTermination(200, TimeUnit.MILLISECONDS) }
    }

    private fun writeFully(data: ByteArray) {
        if (closed.get()) {
            throw SerialException(SerialErrorCode.NOT_CONNECTED, "serial closed: $path")
        }
        try {
            output.write(data)
            output.flush()
        } catch (e: IOException) {
            closeQuietly()
            throw SerialException(SerialErrorCode.WRITE_FAILED, e.message ?: "write failed", e)
        }
    }

    private fun ensureOpen() {
        if (closed.get()) throw SerialException(SerialErrorCode.NOT_CONNECTED, "serial closed: $path")
    }

    companion object {
        private const val READ_POLL_MS = 10L

        /**
         * 按 [SerialConfig] 打开原生串口。
         * @throws SerialException 打开失败
         */
        fun open(config: SerialConfig): NativeSerialTransport {
            val path = config.path.trim()
            if (path.isEmpty()) {
                throw SerialException(SerialErrorCode.INVALID_ARGUMENT, "serial path is blank")
            }
            val file = File(path)
            if (!file.exists()) {
                throw SerialException(SerialErrorCode.OPEN_FAILED, "device not found: $path")
            }
            return try {
                val port = SerialPort
                    .newBuilder(path, config.baudRate)
                    .dataBits(config.dataBits.value)
                    .parity(config.parity.value)
                    .stopBits(config.stopBits.value)
                    .build()
                NativeSerialTransport(path, port)
            } catch (e: SerialException) {
                throw e
            } catch (e: Exception) {
                throw SerialException(
                    SerialErrorCode.OPEN_FAILED,
                    e.message ?: "open serial failed: $path",
                    e
                )
            }
        }
    }
}
