package com.xiaoyao.modbus.transport

import com.xiaoyao.modbus.exception.ModbusException
import com.xiaoyao.modbus.frame.ModbusResponseMatcher
import com.xiaoyao.modbus.frame.ModbusRtuFrameExtractor
import com.xiaoyao.modbus.model.ModbusRequestContext
import com.xiaoyao.serial.connection.SerialConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.milliseconds

/**
 * 基于 [SerialConnection] 的 Modbus RTU 传输实现（增强版）。
 *
 * 相对基础匹配额外加强：
 * - 发前 drain + [preSendSettleMs] 静默，尽量吃掉慢到的旧字节
 * - 收成功后再 [postReceiveSettleMs] 并再次 drain，丢掉粘在后面的多余数据
 */
class SerialModbusTransport(
    private val connection: SerialConnection,
    private val baudRate: Int = 9600,
    private val frameGapMsOverride: Long? = null,
    /** 发请求前，drain 之后再等待的静默时间 */
    private val preSendSettleMs: Long = 20L,
    /** 收齐合法帧后，再等待并清空残留的时间 */
    private val postReceiveSettleMs: Long = 20L
) : ModbusTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val bufferLock = Any()
    private val rxBuffer = ByteArrayOutputStream(256)
    private val dataSignal = Channel<Unit>(Channel.CONFLATED)

    private val collectorJob: Job = scope.launch {
        connection.incomingBytes.collect { chunk ->
            if (chunk.isEmpty()) return@collect
            synchronized(bufferLock) {
                rxBuffer.write(chunk)
            }
            dataSignal.trySend(Unit)
        }
    }

    private val frameGapMs: Long
        get() = frameGapMsOverride ?: ((11.0 / baudRate) * 3.5 * 1000.0)
            .toLong()
            .coerceAtLeast(5L)

    override suspend fun transact(
        request: ByteArray,
        context: ModbusRequestContext,
        timeoutMs: Long
    ): ByteArray = mutex.withLock {
        if (!connection.isConnected()) {
            throw ModbusException(ModbusException.TRANSPORT_ERROR, "serial not connected")
        }
        if (request.size < 4) {
            throw ModbusException(ModbusException.INVALID_ARGUMENT, "request too short")
        }

        // 1) 清空旧数据
        drainReceiveBuffer()
        // 2) 再静默一会，让迟到的旧应答进缓冲后一并清掉
        if (preSendSettleMs > 0) {
            delay(preSendSettleMs.milliseconds)
            drainReceiveBuffer()
        }

        try {
            // 超时只覆盖「写 + 等应答」；收后静默清尾巴不得计入 timeout，
            // 否则边界情况下合法帧已到手仍会 TimeoutCancellationException。
            val frame = withTimeout(timeoutMs.milliseconds) {
                connection.write(request).getOrElse { e ->
                    throw ModbusException(
                        ModbusException.TRANSPORT_ERROR,
                        e.message ?: "write failed",
                        cause = e
                    )
                }
                val response = awaitResponseFrame(context, timeoutMs)
                ModbusResponseMatcher.assertMatches(response, context)
                response
            }
            // 3) 成功后丢弃应答后面可能粘上的多余字节，避免污染下一笔
            discardTrailingBytes()
            frame
        } catch (e: TimeoutCancellationException) {
            drainReceiveBuffer()
            throw ModbusException(
                ModbusException.TIMEOUT,
                "modbus response timeout ${timeoutMs}ms",
                cause = e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ModbusException) {
            // 失败也清空，避免半包留到下次
            drainReceiveBuffer()
            throw e
        } catch (e: Exception) {
            drainReceiveBuffer()
            throw ModbusException(
                ModbusException.TRANSPORT_ERROR,
                e.message ?: "transact failed",
                cause = e
            )
        }
    }

    fun close() {
        collectorJob.cancel()
        scope.cancel()
        dataSignal.close()
        drainReceiveBuffer()
    }

    /** 收齐目标帧后：短等 + drain，清掉粘包尾巴 */
    private suspend fun discardTrailingBytes() {
        if (postReceiveSettleMs > 0) {
            delay(postReceiveSettleMs.milliseconds)
        }
        drainReceiveBuffer()
    }

    private fun drainReceiveBuffer() {
        synchronized(bufferLock) {
            rxBuffer.reset()
        }
        while (dataSignal.tryReceive().isSuccess) {
            // drain signal
        }
    }

    private fun snapshotBuffer(): ByteArray = synchronized(bufferLock) {
        rxBuffer.toByteArray()
    }

    private fun consumeBuffer(count: Int) {
        if (count <= 0) return
        synchronized(bufferLock) {
            val all = rxBuffer.toByteArray()
            rxBuffer.reset()
            if (count < all.size) {
                rxBuffer.write(all, count, all.size - count)
            }
        }
    }

    private suspend fun awaitResponseFrame(
        context: ModbusRequestContext,
        timeoutMs: Long
    ): ByteArray {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L

        while (true) {
            val snapshot = snapshotBuffer()
            val extracted = ModbusRtuFrameExtractor.tryExtract(snapshot, context)

            if (extracted.consumed > 0) {
                consumeBuffer(extracted.consumed)
            }
            extracted.frame?.let { return it }

            val remainMs = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
            if (remainMs == 0L) {
                throw ModbusException(ModbusException.TIMEOUT, "modbus response timeout")
            }

            val waitMs = minOf(remainMs, frameGapMs)
            val gotData = awaitDataOrTimeout(waitMs)
            if (!gotData) {
                val again = snapshotBuffer()
                if (again.isNotEmpty()) {
                    val retry = ModbusRtuFrameExtractor.tryExtract(again, context)
                    if (retry.consumed > 0) consumeBuffer(retry.consumed)
                    retry.frame?.let { return it }
                }
                if (System.nanoTime() >= deadline) {
                    throw ModbusException(ModbusException.TIMEOUT, "modbus response timeout")
                }
            }
        }
    }

    private suspend fun awaitDataOrTimeout(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0L) return false
        if (snapshotBuffer().isNotEmpty()) return true
        return try {
            withTimeout(timeoutMs.milliseconds) {
                dataSignal.receive()
                true
            }
        } catch (_: TimeoutCancellationException) {
            false
        }
    }
}
