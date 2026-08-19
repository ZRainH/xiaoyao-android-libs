package com.xiaoyao.serial.connection

import com.xiaoyao.serial.exception.SerialErrorCode
import com.xiaoyao.serial.exception.SerialException
import com.xiaoyao.serial.model.SerialConfig
import com.xiaoyao.serial.model.SerialConnectionState
import com.xiaoyao.serial.protocol.RawProtocolAdapter
import com.xiaoyao.serial.protocol.SerialProtocolAdapter
import com.xiaoyao.serial.transport.SerialTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * 单路串口会话：底层 [SerialTransport] + 可选协议拆帧 + 请求应答 [execute] / 异步 [enqueue]。
 */
class SerialConnection internal constructor(
    val connectionId: String,
    private val transport: SerialTransport,
    private val config: SerialConfig,
    private val protocol: SerialProtocolAdapter = RawProtocolAdapter,
    private val onClosed: ((connectionId: String) -> Unit)? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val executeMutex = Mutex()
    private val protocolLock = Any()
    private val pendingLock = Any()
    private val pendingExecutes = ArrayDeque<PendingExecute>()
    private val disposed = AtomicBoolean(false)

    private val _state = MutableStateFlow(SerialConnectionState.CONNECTED)
    val state: StateFlow<SerialConnectionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingBytes: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    private val _frames = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingFrames: SharedFlow<ByteArray> = _frames.asSharedFlow()

    private var readJob: Job? = null

    val transportName: String get() = transport.name
    val protocolName: String get() = protocol.name

    init {
        startReadLoop()
    }

    fun isConnected(): Boolean = _state.value == SerialConnectionState.CONNECTED

    suspend fun write(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            if (!isOpen()) {
                return@withContext notConnected()
            }
            try {
                transport.write(data, config.writeTimeoutMs)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val ex = e as? SerialException
                    ?: SerialException(SerialErrorCode.WRITE_FAILED, e.message ?: "write failed", e)
                if (isFatal(ex)) closeWithError(ex)
                Result.failure(ex)
            }
        }
    }

    suspend fun send(payload: ByteArray): Result<Unit> {
        val wire = synchronized(protocolLock) { protocol.encode(payload) }
        return write(wire)
    }

    suspend fun writeText(text: String, charset: Charset = Charsets.UTF_8): Result<Unit> =
        write(text.toByteArray(charset))

    suspend fun writeHex(hex: String): Result<Unit> {
        val bytes = runCatching { hexToBytes(hex) }.getOrElse { e ->
            return Result.failure(
                SerialException(SerialErrorCode.WRITE_FAILED, e.message ?: "invalid hex", e)
            )
        }
        return write(bytes)
    }

    /**
     * 发请求并等待 [matcher] 命中的应答；超时返回 [SerialErrorCode.TIMEOUT]。
     *
     * 流程：先登记 pending → 再写请求 → 读循环 [tryCompleteExecute] 命中后唤醒本调用。
     * 匹配到的帧不进 [incomingFrames]；未匹配的帧（主动上报）仍走 [incomingFrames]。
     *
     * @param request 要发出的字节；[encode] 为 true 时先走 [protocol.encode]
     * @param timeoutMs 等待应答超时，默认 [SerialConfig.executeTimeoutMs]
     * @param encode true 时把 [request] 当载荷编码后再写；false 则原样写出
     * @param matcher 判断「这帧是不是本笔应答」；应包含 messageId / 流水号等唯一字段
     * @return 命中帧；写失败、超时、未连接等为 [Result.failure]
     */
    suspend fun execute(
        request: ByteArray,
        timeoutMs: Long = config.executeTimeoutMs,
        encode: Boolean = false,
        matcher: (ByteArray) -> Boolean
    ): Result<ByteArray> {
        suspend fun once(): Result<ByteArray> {
            if (!isOpen()) return notConnected()
            // 先入队再写，避免应答比 pending 登记更早到达而漏匹配
            val pending = PendingExecute(matcher)
            synchronized(pendingLock) { pendingExecutes.addLast(pending) }
            try {
                val wire = if (encode) {
                    synchronized(protocolLock) { protocol.encode(request) }
                } else {
                    request
                }
                write(wire).onFailure { e ->
                    removePending(pending)
                    return Result.failure(
                        e as? SerialException ?: SerialException(SerialErrorCode.WRITE_FAILED, e.message ?: "write failed", e)
                    )
                }
                return try {
                    // 读循环命中 matcher 后会 complete(deferred)，此处拿到应答帧
                    val frame = withTimeout(timeoutMs.coerceAtLeast(1L).milliseconds) {
                        pending.deferred.await()
                    }
                    Result.success(frame)
                } catch (_: TimeoutCancellationException) {
                    // 超时摘掉 pending；迟到帧可能随后出现在 incomingFrames
                    removePending(pending)
                    Result.failure(
                        SerialException(SerialErrorCode.TIMEOUT, "execute timeout ${timeoutMs}ms")
                    )
                }
            } catch (e: CancellationException) {
                removePending(pending)
                throw e
            } catch (e: Exception) {
                removePending(pending)
                val ex = e as? SerialException
                    ?: SerialException(SerialErrorCode.UNKNOWN, e.message ?: "execute failed", e)
                return Result.failure(ex)
            }
        }
        // serializeExecute=true 时同一时刻只允许一笔，无流水号协议靠串行保证对应
        return if (config.serializeExecute) executeMutex.withLock { once() } else once()
    }

    /**
     * 异步发请求：立即返回 [Job]，不挂起调用方。
     *
     * 在连接 [scope] 中执行 [execute]，结果通过 [onComplete] 回调（成功 / 失败 / 超时）。
     * 取消返回的 [Job] 会中断等待，并摘掉对应 pending。
     *
     * @param onComplete 在 IO 协程中调用，更新 UI 请自行切到 Main
     * @return 可用于 [Job.cancel] 取消本笔请求
     */
    fun enqueue(
        request: ByteArray,
        timeoutMs: Long = config.executeTimeoutMs,
        encode: Boolean = false,
        matcher: (ByteArray) -> Boolean,
        onComplete: (Result<ByteArray>) -> Unit
    ): Job = scope.launch {
        try {
            onComplete(execute(request, timeoutMs, encode, matcher))
        } catch (e: CancellationException) {
            onComplete(Result.failure(SerialException(SerialErrorCode.NOT_CONNECTED, "enqueue cancelled", e)))
        }
    }

    fun close() {
        if (!disposed.compareAndSet(false, true)) return
        _state.value = SerialConnectionState.DISCONNECTED
        failAllPending(SerialException(SerialErrorCode.NOT_CONNECTED, "serial closed"))
        readJob?.cancel()
        readJob = null
        synchronized(protocolLock) { protocol.reset() }
        runCatching { transport.close() }
        runCatching { onClosed?.invoke(connectionId) }
        scope.cancel()
    }

    private fun closeWithError(error: SerialException) {
        if (!disposed.compareAndSet(false, true)) return
        _state.value = SerialConnectionState.ERROR
        failAllPending(error)
        readJob?.cancel()
        readJob = null
        synchronized(protocolLock) { protocol.reset() }
        runCatching { transport.close() }
        runCatching { onClosed?.invoke(connectionId) }
        scope.cancel()
    }

    /**
     * 后台读循环：从 [transport] 取字节 → 拆帧 → 优先交给 pending [execute]，否则发到 [incomingFrames]。
     */
    private fun startReadLoop() {
        readJob = scope.launch {
            val buffer = ByteArray(4096)
            while (isActive && isOpen()) {
                try {
                    // 阻塞读，超时返回 0，不视为断线
                    val len = transport.read(buffer, config.readTimeoutMs)
                    if (len <= 0) continue
                    val chunk = buffer.copyOf(len)
                    _incoming.emit(chunk)
                    // 半包缓冲在 protocol 内，此处拿到的是完整帧
                    val frames = synchronized(protocolLock) { protocol.feed(chunk) }
                    for (frame in frames) {
                        // matcher 命中则完成本笔 execute，不再推送给订阅者
                        if (!tryCompleteExecute(frame)) {
                            _frames.emit(frame)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!isOpen()) break
                    // 非致命超时等忽略，IO 失败则关连接
                    if (isFatal(e)) {
                        val ex = e as? SerialException
                            ?: SerialException(SerialErrorCode.READ_FAILED, e.message ?: "read failed", e)
                        closeWithError(ex)
                        break
                    }
                }
            }
        }
    }

    private fun isOpen(): Boolean = _state.value == SerialConnectionState.CONNECTED

    /**
     * 尝试把 [frame] 交给某笔 pending [execute]。
     *
     * 从队头起找第一个 matcher 为 true 的 pending：命中则 complete 并移出队列。
     * 多笔同时 pending 时先登记先得；matcher 抛异常视为未命中。
     *
     * @return true 表示已被 execute 消费，读循环不要再发到 [incomingFrames]
     */
    private fun tryCompleteExecute(frame: ByteArray): Boolean {
        synchronized(pendingLock) {
            // 从队头扫描：第一个 matcher(frame)==true 的 pending 拿走这帧
            val hit = pendingExecutes.firstOrNull { p ->
                // matcher 抛异常当成未命中，避免读循环被业务代码打崩
                runCatching { p.matcher(frame) }.getOrDefault(false)
            } ?: return false // 无人认领，交给 incomingFrames
            pendingExecutes.remove(hit)
            // 唤醒正在 await 的 execute，把本帧作为应答返回
            hit.deferred.complete(frame)
            return true
        }
    }

    private fun removePending(pending: PendingExecute) {
        synchronized(pendingLock) { pendingExecutes.remove(pending) }
    }

    private fun failAllPending(error: SerialException) {
        synchronized(pendingLock) {
            pendingExecutes.forEach { it.deferred.completeExceptionally(error) }
            pendingExecutes.clear()
        }
    }

    private fun notConnected(): Result<Nothing> =
        Result.failure(SerialException(SerialErrorCode.NOT_CONNECTED, "serial port is not connected"))

    private class PendingExecute(
        val matcher: (ByteArray) -> Boolean,
        val deferred: CompletableDeferred<ByteArray> = CompletableDeferred()
    )

    companion object {
        private const val EVENT_BUFFER = 64

        fun hexToBytes(hex: String): ByteArray {
            val cleaned = hex.replace(" ", "").replace("\n", "").replace("\r", "").replace(":", "")
            require(cleaned.length % 2 == 0) { "hex length must be even" }
            return ByteArray(cleaned.length / 2) { i ->
                cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }

        private fun isFatal(error: Throwable): Boolean {
            var cur: Throwable? = error
            while (cur != null) {
                when (cur) {
                    is IOException -> return true
                    is SerialException -> when (cur.code) {
                        SerialErrorCode.READ_FAILED,
                        SerialErrorCode.WRITE_FAILED,
                        SerialErrorCode.NOT_CONNECTED -> return true
                    }
                }
                cur = cur.cause
            }
            return false
        }
    }
}
