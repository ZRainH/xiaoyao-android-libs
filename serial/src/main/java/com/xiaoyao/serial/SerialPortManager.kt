package com.xiaoyao.serial

import android.serialport.SerialPortFinder
import com.xiaoyao.serial.connection.SerialConnection
import com.xiaoyao.serial.exception.SerialErrorCode
import com.xiaoyao.serial.exception.SerialException
import com.xiaoyao.serial.model.SerialConfig
import com.xiaoyao.serial.model.SerialConnectionState
import com.xiaoyao.serial.model.SerialOpenRequest
import com.xiaoyao.serial.protocol.RawProtocolAdapter
import com.xiaoyao.serial.protocol.SerialProtocolAdapter
import com.xiaoyao.serial.transport.NativeSerialTransport
import com.xiaoyao.serial.transport.SerialTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 多路串口会话管理。
 *
 * - [openPort]：基于 `com.licheedev:android-serialport` 打开原生串口
 * - [open]：登记任意已实现的 [SerialTransport]
 */
class SerialPortManager private constructor() {
    private val lock = Any()
    private val connections = linkedMapOf<String, SerialConnection>()

    private val _connectionsSnapshot =
        MutableStateFlow<Map<String, SerialConnection>>(emptyMap())
    val connectionsSnapshot: StateFlow<Map<String, SerialConnection>> =
        _connectionsSnapshot.asStateFlow()

    /** 枚举本机串口设备路径（如 `/dev/ttyS1`） */
    fun listDevicePaths(): List<String> =
        runCatching {
            SerialPortFinder().allDevicesPath?.filterNotNull()?.filter { it.isNotBlank() }.orEmpty()
        }.getOrDefault(emptyList())

    /** 枚举本机串口设备名 */
    fun listDeviceNames(): List<String> =
        runCatching {
            SerialPortFinder().allDevices?.filterNotNull()?.filter { it.isNotBlank() }.orEmpty()
        }.getOrDefault(emptyList())

    /**
     * 打开原生串口并登记会话。
     *
     * @param connectionId 业务连接 ID
     * @param path 设备节点，如 `/dev/ttyS1`
     * @param baudRate 波特率
     */
    fun openPort(
        connectionId: String,
        path: String,
        baudRate: Int = 9600,
        config: SerialConfig = SerialConfig(),
        protocol: SerialProtocolAdapter = RawProtocolAdapter
    ): Result<SerialConnection> = openPort(
        connectionId = connectionId,
        config = config.copy(path = path, baudRate = baudRate),
        protocol = protocol
    )

    fun openPort(
        connectionId: String,
        config: SerialConfig,
        protocol: SerialProtocolAdapter = RawProtocolAdapter
    ): Result<SerialConnection> {
        val transport = runCatching { NativeSerialTransport.open(config) }.fold(
            onSuccess = { it },
            onFailure = { e ->
                val ex = e as? SerialException
                    ?: SerialException(SerialErrorCode.OPEN_FAILED, e.message ?: "open failed", e)
                return Result.failure(ex)
            }
        )
        val result = open(
            SerialOpenRequest(
                connectionId = connectionId,
                transport = transport,
                config = config,
                protocol = protocol
            )
        )
        if (result.isFailure) {
            runCatching { transport.close() }
        }
        return result
    }

    fun open(
        connectionId: String,
        transport: SerialTransport,
        config: SerialConfig = SerialConfig(),
        protocol: SerialProtocolAdapter = RawProtocolAdapter
    ): Result<SerialConnection> = open(
        SerialOpenRequest(
            connectionId = connectionId,
            transport = transport,
            config = config,
            protocol = protocol
        )
    )

    fun open(request: SerialOpenRequest): Result<SerialConnection> =
        runCatching { openLocked(request) }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { e ->
                val ex = e as? SerialException
                    ?: SerialException(SerialErrorCode.UNKNOWN, e.message ?: "open failed", e)
                Result.failure(ex)
            }
        )

    fun openMany(requests: List<SerialOpenRequest>): Map<String, Result<SerialConnection>> {
        val results = linkedMapOf<String, Result<SerialConnection>>()
        for (request in requests) {
            results[request.connectionId] = open(request)
        }
        return results
    }

    fun getConnection(connectionId: String): SerialConnection? =
        synchronized(lock) { connections[connectionId]?.takeIf { it.isConnected() } }

    fun getAllConnections(): List<SerialConnection> =
        synchronized(lock) { connections.values.filter { it.isConnected() } }

    fun close(connectionId: String) {
        val connection = synchronized(lock) {
            connections.remove(connectionId).also { publishConnectionsLocked() }
        }
        connection?.close()
    }

    fun closeAll() {
        val snapshot = synchronized(lock) {
            val list = connections.values.toList()
            connections.clear()
            publishConnectionsLocked()
            list
        }
        snapshot.forEach { it.close() }
    }

    fun connectionStates(): Map<String, SerialConnectionState> =
        synchronized(lock) { connections.mapValues { (_, conn) -> conn.state.value } }

    /** 会话自关闭（读写致命错误）时摘除登记 */
    private fun onConnectionClosed(connectionId: String, connection: SerialConnection) {
        synchronized(lock) {
            if (connections[connectionId] === connection) {
                connections.remove(connectionId)
                publishConnectionsLocked()
            }
        }
    }

    private fun openLocked(request: SerialOpenRequest): SerialConnection = synchronized(lock) {
        val key = request.connectionId.trim()
        if (key.isEmpty()) {
            throw SerialException(SerialErrorCode.INVALID_ARGUMENT, "connectionId is blank")
        }
        if (!request.transport.isOpen()) {
            throw SerialException(
                SerialErrorCode.OPEN_FAILED,
                "transport is not open: ${request.transport.name}"
            )
        }

        val transportName = request.transport.name
        connections.values.firstOrNull { it.isConnected() && it.transportName == transportName }?.let {
            throw SerialException(
                SerialErrorCode.ALREADY_OPEN,
                "transport already in use: $transportName by ${it.connectionId}"
            )
        }

        connections[key]?.let { existing ->
            if (existing.isConnected()) {
                throw SerialException(SerialErrorCode.ALREADY_OPEN, "already open: $key")
            }
            connections.remove(key)
        }

        lateinit var connection: SerialConnection
        connection = SerialConnection(
            connectionId = key,
            transport = request.transport,
            config = request.config,
            protocol = request.protocol,
            onClosed = { id -> onConnectionClosed(id, connection) }
        )
        connections[key] = connection
        publishConnectionsLocked()
        connection
    }

    private fun publishConnectionsLocked() {
        _connectionsSnapshot.value = connections.toMap()
    }

    companion object {
        @Volatile
        private var instance: SerialPortManager? = null

        fun getInstance(): SerialPortManager {
            return instance ?: synchronized(this) {
                instance ?: SerialPortManager().also { instance = it }
            }
        }
    }
}
