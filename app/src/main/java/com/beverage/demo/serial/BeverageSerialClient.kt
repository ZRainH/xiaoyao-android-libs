package com.beverage.demo.serial

import com.beverage.demo.protocol.DispenseSegment
import com.beverage.demo.protocol.WxChecksum
import com.beverage.demo.protocol.WxCodec
import com.beverage.demo.protocol.WxFunctionCode
import com.beverage.demo.protocol.WxMessage
import com.beverage.demo.protocol.WxPayload
import com.beverage.demo.protocol.WxProtocol
import com.beverage.demo.protocol.WxProtocolAdapter
import com.xiaoyao.serial.SerialPortManager
import com.xiaoyao.serial.connection.SerialConnection
import com.xiaoyao.serial.exception.SerialException
import com.xiaoyao.serial.model.DataBits
import com.xiaoyao.serial.model.Parity
import com.xiaoyao.serial.model.SerialConfig
import com.xiaoyao.serial.model.SerialConnectionState
import com.xiaoyao.serial.model.StopBits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 饮料机串口通信客户端：`/dev/ttyS4` @ 9600，WX 协议。
 *
 * 请求走 [SerialConnection.execute]（按 messageId + 功能码配对，带超时）；
 * 主动上报（0x63 / 0x71 等）仍走 [messages]。
 */
class BeverageSerialClient(
    private val connectionId: String = CONNECTION_ID,
    private val path: String = DEFAULT_PATH,
    private val baudRate: Int = DEFAULT_BAUD,
    private val requestTimeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageIdGen = AtomicInteger(1)

    private val manager = SerialPortManager.getInstance()
    private var connection: SerialConnection? = null
    private var collectJob: Job? = null
    private var stateJob: Job? = null

    private val _state = MutableStateFlow(SerialConnectionState.DISCONNECTED)
    val state: StateFlow<SerialConnectionState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<WxMessage>(extraBufferCapacity = 32)
    /** 未配对的上行（主动通知等） */
    val messages: SharedFlow<WxMessage> = _messages.asSharedFlow()

    private val _rawLog = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val rawLog: SharedFlow<String> = _rawLog.asSharedFlow()

    fun isOpen(): Boolean = connection?.isConnected() == true

    fun open(): Result<Unit> {
        if (isOpen()) return Result.success(Unit)
        val result = manager.openPort(
            connectionId = connectionId,
            path = path,
            baudRate = baudRate,
            config = SerialConfig(
                path = path,
                baudRate = baudRate,
                dataBits = DataBits.BITS_8,
                stopBits = StopBits.BITS_1,
                parity = Parity.NONE,
                readTimeoutMs = 100,
                executeTimeoutMs = requestTimeoutMs,
                serializeExecute = false
            ),
            protocol = WxProtocolAdapter()
        )
        return result.fold(
            onSuccess = { conn ->
                connection = conn
                _state.value = conn.state.value
                startCollect(conn)
                stateJob?.cancel()
                stateJob = scope.launch {
                    conn.state.collect { _state.value = it }
                }
                Result.success(Unit)
            },
            onFailure = { e ->
                _state.value = SerialConnectionState.ERROR
                Result.failure(e)
            }
        )
    }

    fun close() {
        collectJob?.cancel()
        collectJob = null
        stateJob?.cancel()
        stateJob = null
        manager.close(connectionId)
        connection = null
        _state.value = SerialConnectionState.DISCONNECTED
    }

    fun release() {
        close()
        scope.cancel()
    }

    suspend fun stopDispense(
        address: Int = WxProtocol.DEFAULT_ADDRESS,
        screenCommand: Int = WxProtocol.DEFAULT_SCREEN,
        timeoutMs: Long = requestTimeoutMs
    ): Result<WxMessage> = request(WxPayload.StopRequest, address, screenCommand, timeoutMs)

    suspend fun segmentDispense(
        segments: List<DispenseSegment>,
        address: Int = WxProtocol.DEFAULT_ADDRESS,
        screenCommand: Int = WxProtocol.DEFAULT_SCREEN,
        timeoutMs: Long = requestTimeoutMs
    ): Result<WxMessage> = request(
        WxPayload.SegmentDispenseRequest(segments),
        address,
        screenCommand,
        timeoutMs
    )

    suspend fun clean(
        totalPin: Int,
        totalForward: Boolean,
        speed: Int,
        loopPin: Int,
        loopDirection: Int,
        loopDurationMs: Int,
        address: Int = WxProtocol.DEFAULT_ADDRESS,
        screenCommand: Int = WxProtocol.DEFAULT_SCREEN,
        timeoutMs: Long = requestTimeoutMs
    ): Result<WxMessage> = request(
        WxPayload.CleanRequest(
            totalPin = totalPin,
            totalForward = totalForward,
            speed = speed,
            loopPin = loopPin,
            loopDirection = loopDirection,
            loopDurationMs = loopDurationMs
        ),
        address,
        screenCommand,
        timeoutMs
    )

    /**
     * 发请求并等待同一 messageId + 功能码的应答。
     */
    suspend fun request(
        payload: WxPayload,
        address: Int = WxProtocol.DEFAULT_ADDRESS,
        screenCommand: Int = WxProtocol.DEFAULT_SCREEN,
        timeoutMs: Long = requestTimeoutMs,
        messageId: Int = nextMessageId()
    ): Result<WxMessage> {
        val conn = connection
            ?: return Result.failure(SerialException("NOT_CONNECTED", "serial not open"))
        val (functionCode, _) = WxCodec.encodePayload(payload)
        val frame = WxCodec.buildMessage(payload, address, screenCommand, messageId)
        _rawLog.emit("TX ${WxCodec.toHex(frame)}")
        return conn.execute(frame, timeoutMs) { raw ->
            matchesResponse(raw, address, messageId, functionCode)
        }.fold(
            onSuccess = { raw ->
                _rawLog.emit("RX ${WxCodec.toHex(raw)}")
                runCatching { WxCodec.parseMessage(raw) }
            },
            onFailure = { e ->
                _rawLog.emit("TX-FAIL ${e.message}")
                Result.failure(e)
            }
        )
    }

    /** 只发不等待（调试 / 不需要应答时） */
    suspend fun sendFireAndForget(
        payload: WxPayload,
        address: Int = WxProtocol.DEFAULT_ADDRESS,
        screenCommand: Int = WxProtocol.DEFAULT_SCREEN,
        messageId: Int = nextMessageId()
    ): Result<Unit> {
        val conn = connection
            ?: return Result.failure(SerialException("NOT_CONNECTED", "serial not open"))
        val frame = WxCodec.buildMessage(payload, address, screenCommand, messageId)
        _rawLog.emit("TX ${WxCodec.toHex(frame)}")
        return conn.write(frame).onFailure { e ->
            _rawLog.emit("TX-FAIL ${e.message}")
        }
    }

    private fun startCollect(conn: SerialConnection) {
        collectJob?.cancel()
        collectJob = scope.launch {
            conn.incomingFrames.collect { frameBytes ->
                _rawLog.emit("NOTIFY ${WxCodec.toHex(frameBytes)}")
                runCatching {
                    WxCodec.parseMessage(frameBytes)
                }.onSuccess { msg ->
                    _messages.emit(msg)
                }.onFailure { e ->
                    _rawLog.emit("PARSE-FAIL ${e.message}")
                }
            }
        }
    }

    private fun nextMessageId(): Int {
        val id = messageIdGen.getAndUpdate { cur -> if (cur >= 0xFFFF) 1 else cur + 1 }
        return id
    }

    companion object {
        const val CONNECTION_ID = "beverage-main"
        const val DEFAULT_PATH = "/dev/ttyS4"
        const val DEFAULT_BAUD = 9600
        const val DEFAULT_TIMEOUT_MS = 1000L

        fun matchesResponse(
            raw: ByteArray,
            address: Int,
            messageId: Int,
            functionCode: Int
        ): Boolean {
            if (raw.size < 8) return false
            if (raw[0] != WxProtocol.HEADER[0] || raw[1] != WxProtocol.HEADER[1]) return false
            if (!WxChecksum.verify(raw)) return false
            val rspAddress = raw[2].toInt() and 0xFF
            val rspMsgId = ((raw[4].toInt() and 0xFF) shl 8) or (raw[5].toInt() and 0xFF)
            val rspFunc = raw[6].toInt() and 0xFF
            return rspAddress == address && rspMsgId == messageId && rspFunc == functionCode
        }

        fun statusText(status: Int): String = when (status) {
            0x00 -> "成功"
            0x0E -> "任务执行中(占用)"
            0x10 -> "缺料/错误"
            0x11 -> "正在运行"
            else -> "未知(0x%02X)".format(status)
        }

        fun functionName(code: Int): String = when (code) {
            WxFunctionCode.PIN_DISPENSE -> "单引脚出料(0x20)"
            WxFunctionCode.STOP_DISPENSE -> "停止出料(0x22)"
            WxFunctionCode.SEGMENT_DISPENSE -> "分段出料(0x62)"
            WxFunctionCode.DISPENSE_FINISHED -> "出料结束(0x63)"
            WxFunctionCode.CLEAN -> "清洗(0x70)"
            WxFunctionCode.CLEAN_FINISHED -> "清洗结束(0x71)"
            else -> "功能(0x%02X)".format(code)
        }
    }
}
