package cn.xiaoyao.bluetooth.manager

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import cn.xiaoyao.bluetooth.R
import cn.xiaoyao.bluetooth.connection.BleConnection
import cn.xiaoyao.bluetooth.exception.BLUETOOTH_PERMISSION_REQUIRED
import cn.xiaoyao.bluetooth.exception.BluetoothException
import cn.xiaoyao.bluetooth.exception.DEVICE_NOT_CONNECT
import cn.xiaoyao.bluetooth.exception.OTHER_ERROR
import cn.xiaoyao.bluetooth.exception.RESPONSE_TIMEOUT
import cn.xiaoyao.bluetooth.exception.SEND_COMMAND_FAILED
import cn.xiaoyao.bluetooth.exception.SERVICE_CONFIG_NOT_FOUND
import cn.xiaoyao.bluetooth.model.BleServiceProfile
import cn.xiaoyao.bluetooth.model.ConnectMode
import cn.xiaoyao.bluetooth.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * 蓝牙连接管理器 - 支持多设备连接和数据接收
 */
class BleConnectionManager private constructor(private val context: Context) {
    private val TAG = "BleConnectionManager"

    // 连接管理
    private val connectionPool = ConcurrentHashMap<String, ConnectionEntry>()

    // 响应等待管理
    private val pendingResponses = ConcurrentHashMap<String, ResponseChannel>()

    // 指令发送锁，确保同一设备同一时间只有一个同步指令在执行
    private val deviceLocks = ConcurrentHashMap<String, Mutex>()

    // 数据流管理
    private val _activePushes = MutableSharedFlow<DeviceMessage>(extraBufferCapacity = 256)
    val activePushes: SharedFlow<DeviceMessage> = _activePushes

    // 连接状态流
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    // 全局协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 设备分组管理（可选）
    private val deviceGroups = ConcurrentHashMap<String, MutableSet<String>>()

    private val monitorJobs = ConcurrentHashMap<String, Job>()

    init {
        // 启动全局消息处理器
        //  startGlobalMessageHandler()
    }

    /**
     * 连接条目 - 包装连接和相关状态
     */
    private data class ConnectionEntry(
        val connection: BleConnection,
        val lastActiveTime: Long = System.currentTimeMillis(),
        var isProcessing: Boolean = false
    )

    /**
     * 响应通道包装
     */
    private data class ResponseChannel(
        val channel: Channel<DeviceMessage>,
        val timeoutJob: Job,
        val deviceAddress: String,
        val characteristicUuid: UUID,
        val messageId: String
    )

    /**
     * 启动全局消息处理器
     */
    private fun startGlobalMessageHandler() {
        scope.launch {
            // 监听连接池变化，为新连接注册监听器
            while (isActive) {
                delay(2000)
                Log.e(TAG, "startGlobalMessageHandler: ")
                connectionPool.values.forEach { entry ->
                    if (!entry.isProcessing) {
                        entry.isProcessing = true
                        launch { monitorConnectionMessages(entry.connection) }
                    }
                }
            }
        }
    }

    /**
     * 监控单个连接的消息
     */
    private suspend fun monitorConnectionMessages(connection: BleConnection) {
        val deviceAddress = connection.bluetoothDevice.address

        try {
            // 收集特征数据流
            connection.characteristicData.collect { (characteristicUuid, data) ->
                if (data == null) return@collect

                // 查找服务解析器
                val serviceProfile =
                    connection.findServiceProfileForCharacteristic(characteristicUuid)
                val messageId = serviceProfile?.serviceInterpreter?.generateMessageId(data)

                // 构建设备消息
                val message = DeviceMessage(
                    deviceAddress = deviceAddress,
                    deviceName = connection.bluetoothDevice.name ?: "Unknown",
                    characteristicUuid = characteristicUuid,
                    messageId = messageId,
                    data = data,
                    timestamp = System.currentTimeMillis(),
                    rawBytes = data as? ByteArray
                )
                // 处理消息
                if (messageId != null) {
                    val responseKey = "${deviceAddress}_$messageId"
                    handlePotentialResponse(message, responseKey)
                } else {
                    // 主动推送消息
                    emitActivePush(message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "监控连接消息失败: $deviceAddress", e)
            // 标记连接可重新处理
            connectionPool[deviceAddress]?.isProcessing = false
        }
    }

    /**
     * 处理可能的响应消息
     */
    private suspend fun handlePotentialResponse(message: DeviceMessage, responseKey: String) {
        val responseChannel = pendingResponses[responseKey]

        if (responseChannel != null &&
            responseChannel.deviceAddress == message.deviceAddress &&
            responseChannel.characteristicUuid == message.characteristicUuid
        ) {
            // 匹配到等待的响应
            try {
                responseChannel.channel.send(message)
                responseChannel.channel.close()
                responseChannel.timeoutJob.cancel()
                pendingResponses.remove(responseKey)
            } catch (e: Exception) {
                // 通道已关闭，发送主动推送
                emitActivePush(message)
            }
        } else {
            // 没有匹配的等待指令，作为主动推送
            emitActivePush(message)
        }
    }

    /**
     * 发送主动推送消息（带缓冲和背压处理）
     */
    private suspend fun emitActivePush(message: DeviceMessage) {
        if (!_activePushes.tryEmit(message)) {
            // 如果缓冲区满，等待或丢弃旧消息（根据需求调整策略）
            try {
                Log.e(TAG, "emitActivePush: 推送")
                _activePushes.emit(message)
            } catch (e: Exception) {
                Log.w(TAG, "主动推送消息丢弃: ${message.deviceAddress}")
            }
        }
    }

    // ------------------------------ 连接管理 ------------------------------

    /**
     * 获取或创建连接（线程安全）
     */
    @Synchronized
    fun getOrCreateConnection(
        device: BluetoothDevice,
        connectMode: ConnectMode = ConnectMode.MANUAL,
        serviceProfiles: List<BleServiceProfile> = emptyList(),
        connectionTag: String? = null // 可选的分组标签
    ): BleConnection {
        val address = device.address

        return connectionPool[address]?.connection ?: run {
            val newConnection = BleConnection(
                context = context,
                bluetoothDevice = device,
                connectMode = connectMode,
                serviceProfiles = serviceProfiles
            )

            val entry = ConnectionEntry(newConnection)
            connectionPool[address] = entry

            // 监听连接状态变化
            observeConnectionState(newConnection)

            // 添加到设备分组（可选）
            connectionTag?.let { tag ->
                deviceGroups.getOrPut(tag) { mutableSetOf() }.add(address)
            }

            newConnection
        }
    }

    /**
     * 监听连接状态变化
     */
    private fun observeConnectionState(connection: BleConnection) {
        val address = connection.bluetoothDevice.address
        scope.launch(Dispatchers.Main.immediate) {
            connection.connectionState.observeForever { state ->
                _connectionStates.value = _connectionStates.value.toMutableMap().apply { put(address, state) }

                if (state == ConnectionState.Connected) {
                    // 先取消旧的
                    monitorJobs[address]?.cancel()
                    // 保存新的 Job
                    monitorJobs[address] = scope.launch { monitorConnectionMessages(connection) }
                } else if (state == ConnectionState.Disconnected || state is ConnectionState.Error) {
                    // 断开连接时，立即停止监听数据流，防止协程泄漏
                    monitorJobs[address]?.cancel()
                    monitorJobs.remove(address)

                    scope.launch {
                        delay(1500) // 等待底层真正释放硬件
                        cleanupDeviceIfNotReconnecting(address)
                    }
                }
            }
        }
    }

    /**
     * 安全清理设备连接（检查状态后再清理）
     */
    fun cleanupDeviceIfNotReconnecting(address: String) {
        val connection = connectionPool[address]?.connection
        val state = connection?.connectionState?.value

        when (state) {
            is ConnectionState.Connected -> {
                Log.d(TAG, "设备 $address 已连接，跳过清理")
                return
            }
            is ConnectionState.Connecting -> {
                Log.d(TAG, "设备 $address 正在连接中，跳过清理")
                return
            }
            else -> {
                // 可以清理
                cleanupDisconnectedConnection(address)
            }
        }
    }

    /**
     * 清理断开连接的资源
     */
    private fun cleanupDisconnectedConnection(address: String) {
        // 清理等待的响应
        pendingResponses.values.removeIf { it.deviceAddress == address }

        // 从连接池移除
           connectionPool.remove(address)

        // 从所有分组中移除
        deviceGroups.values.forEach { it.remove(address) }

        // 清理锁资源
        deviceLocks.remove(address)

        Log.d(TAG, "清理断开连接资源: $address")
    }

    /**
     * 获取设备连接
     */
    fun getConnection(address: String): BleConnection? = connectionPool[address]?.connection

    /**
     * 获取所有活跃连接
     */
    fun getAllConnections(): List<BleConnection> =
        connectionPool.values.map { it.connection }

    /**
     * 获取分组连接
     */
    fun getConnectionsByTag(tag: String): List<BleConnection> =
        deviceGroups[tag]?.mapNotNull { getConnection(it) } ?: emptyList()

    /**
     * 断开设备连接
     */
    suspend fun disconnectDevice(
        address: String,
        manual: Boolean = true,
        removeFromPool: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val connection = getConnection(address) ?: return@withContext false

        try {
            connection.disconnect(manual)

            if (removeFromPool) {
                // 不立刻移出缓存池，等待底层 gatt 真正关闭
                scope.launch {
                    delay(1500)
                    cleanupDeviceIfNotReconnecting(address)
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "断开连接失败: $address", e)
            false
        }
    }

    /**
     * 断开所有连接
     */
    suspend fun disconnectAll(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()

        connectionPool.keys.forEach { address ->
            results[address] = try {
                getConnection(address)?.disconnect(true) ?: false
                true
            } catch (e: Exception) {
                false
            }
        }

        // 清理所有资源
        connectionPool.clear()
        pendingResponses.clear()
        deviceLocks.clear()
        _connectionStates.value = emptyMap()

        results
    }

    /**
     * 断开分组连接
     */
    suspend fun disconnectByTag(tag: String): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val addresses = deviceGroups[tag]?.toList() ?: emptyList()
        val results = mutableMapOf<String, Boolean>()

        addresses.forEach { address ->
            results[address] = disconnectDevice(address)
        }

        deviceGroups.remove(tag)
        results
    }

    // ------------------------------ 设备扫描 ------------------------------

    /**
     * 通过MAC地址获取设备（不扫描）
     */
    suspend fun getBluetoothDeviceByMacNoScan(mac: String): Result<BluetoothDevice> =
        suspendCancellableCoroutine { continuation ->
            try {
                val bluetoothManager =
                    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter

                if (adapter == null || !adapter.isEnabled) {
                    continuation.resume(Result.failure(Exception("蓝牙不可用")))
                    return@suspendCancellableCoroutine
                }

                val device = try {
                    adapter.getRemoteDevice(mac)
                } catch (e: IllegalArgumentException) {
                    continuation.resume(Result.failure(IllegalArgumentException("MAC地址格式错误: $mac")))
                    return@suspendCancellableCoroutine
                }

                continuation.resume(Result.success(device))
            } catch (e: Exception) {
                continuation.resume(Result.failure(Exception("获取设备失败: ${e.message}")))
            }
        }

    private fun getBluetoothScanner() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
            ?.bluetoothLeScanner

    /**
     * 批量扫描设备
     */
    suspend fun scanDevices(
        macAddresses: List<String>,
        timeoutMillis: Long = 10000L
    ): Map<String, Result<ScanResult>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Result<ScanResult>>()
        val scanner = getBluetoothScanner() ?: return@withContext results

        // 为每个设备创建独立的等待机制
        val completions = macAddresses.associateWith { CompletableDeferred<ScanResult>() }
        val callback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address

                completions[address]?.let { deferred ->
                    if (!deferred.isCompleted) {
                        deferred.complete(result)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                completions.values.forEach { deferred ->
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(Exception("扫描失败: $errorCode"))
                    }
                }
            }
        }

        // 创建扫描过滤器
        val filters = macAddresses.map { mac ->
            ScanFilter.Builder().setDeviceAddress(mac).build()
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, callback)

            // 等待所有设备扫描完成或超时
            val scanJobs = completions.map { (address, deferred) ->
                scope.async {
                    try {
                        withTimeout(timeoutMillis) {
                            val result = deferred.await()
                            results[address] = Result.success(result)
                        }
                    } catch (e: TimeoutException) {
                        results[address] = Result.failure(TimeoutException("扫描超时: $address"))
                    } catch (e: Exception) {
                        results[address] = Result.failure(e)
                    }
                }
            }

            scanJobs.awaitAll()
        } finally {
            scanner.stopScan(callback)
        }

        results
    }

    // ------------------------------ 指令发送 ------------------------------

    /**
     * 向单个设备发送无响应写入（WRITE_TYPE_NO_RESPONSE）
     */
    fun sendCommandSyncNoResponse(
        deviceAddress: String,
        characteristicUuid: UUID,
        data: Any
    ): Boolean {
        val connection = getConnection(deviceAddress)

        val success = connection?.run {
            this.writeCharacteristic(
                characteristicUuid = characteristicUuid,
                data = data,
                writeType = WRITE_TYPE_NO_RESPONSE
            )
        } ?: false
        return success
    }

    /**
     * 同步发送指令并等待响应（支持单个设备）
     * 优化：使用 Mutex 锁确保同一设备的指令排队发送，避免并发冲突
     */
    suspend fun sendCommandSync(
        deviceAddress: String,
        characteristicUuid: UUID,
        data: Any,
        timeoutMillis: Long = 5000L
    ): Result<DeviceMessage> = withContext(Dispatchers.IO) {
        // 获取或创建该设备的互斥锁
        val mutex = deviceLocks.getOrPut(deviceAddress) { Mutex() }

        mutex.withLock {
            // 权限检查
            if (!checkBluetoothPermission()) {
                return@withLock Result.failure(
                    BluetoothException(
                        BLUETOOTH_PERMISSION_REQUIRED,
                        getString(R.string.s_bluetooth_permission_required)
                    )
                )
            }

            // 连接检查
            val connection = getConnection(deviceAddress)
                .takeIf { it?.isConnected() == true }
                ?: return@withLock Result.failure(
                    BluetoothException(
                        DEVICE_NOT_CONNECT,
                        getString(R.string.s_device_no_connect)
                    )
                )

            // 生成消息ID
            val serviceProfile = connection.findServiceProfileForCharacteristic(characteristicUuid)
                ?: run {
                    return@withLock Result.failure(
                        BluetoothException(
                            SERVICE_CONFIG_NOT_FOUND,
                            getString(R.string.s_service_config_not_found)
                        )
                    )
                }

            val messageId = serviceProfile.serviceInterpreter.generateMessageId(data)

            // 创建响应通道 (使用唯一的 responseKey 避免跨设备冲突)
            val responseChannel = Channel<DeviceMessage>(Channel.RENDEZVOUS)
            val responseKey = "${deviceAddress}_$messageId"
          //  Log.e(TAG, "messageId: $messageId, responseKey: $responseKey")

            // 超时处理
            val timeoutJob = scope.launch {
                delay(timeoutMillis)
                pendingResponses.remove(responseKey)?.let {
                    it.channel.close(
                        BluetoothException(
                            RESPONSE_TIMEOUT,
                            getString(R.string.s_response_timeout)
                        )
                    )
                }
            }

            // 注册等待响应
            pendingResponses[responseKey] = ResponseChannel(
                channel = responseChannel,
                timeoutJob = timeoutJob,
                deviceAddress = deviceAddress,
                characteristicUuid = characteristicUuid,
                messageId = messageId
            )

            try {
                // 发送指令
                val writeSuccess = connection.writeCharacteristic(
                    characteristicUuid = characteristicUuid,
                    data = data,
                    writeType = WRITE_TYPE_DEFAULT
                )

                if (!writeSuccess) {
                    pendingResponses.remove(responseKey)
                    timeoutJob.cancel()
                    return@withLock Result.failure(
                        BluetoothException(
                            SEND_COMMAND_FAILED,
                            getString(R.string.s_send_command_failed)
                        )
                    )
                }

                // 等待响应
                val response = withTimeout(timeoutMillis + 200) {
                    responseChannel.receive()
                }

                Result.success(response)
            }
            catch (e: TimeoutCancellationException) { // 必须捕获这个
                Log.e(TAG, "sendCommandSync: 响应超时")
                Result.failure(
                    BluetoothException(
                        RESPONSE_TIMEOUT,
                        getString(R.string.s_response_timeout)
                    )
                )
            }

            catch (e: TimeoutException) {
                Log.e(TAG, "sendCommandSync: 响应超时")
                Result.failure(
                    BluetoothException(
                        RESPONSE_TIMEOUT,
                        getString(R.string.s_response_timeout)
                    )
                )
            } catch (e: CancellationException) {
                Log.e(TAG, "sendCommandSync Cancellation: $e")
                Result.failure(
                    BluetoothException(
                        OTHER_ERROR,
                        ""
                    )
                )
            } catch (e: BluetoothException) {
                Log.e(TAG, "${e.message} -- $messageId")
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "sendCommandSync Exception: $e")
                Result.failure(
                    BluetoothException(
                        OTHER_ERROR,
                        ""
                    )
                )
            } finally {
                // 清理资源
                pendingResponses.remove(responseKey)
                timeoutJob.cancel()
                responseChannel.close()
            }
        }
    }

    /**
     * 批量发送指令到多个设备
     */
    suspend fun sendCommandsToMultiple(
        deviceAddresses: List<String>,
        characteristicUuid: UUID,
        data: Any,
        timeoutMillis: Long = 5000L,
        parallel: Boolean = true // 是否并行发送
    ): Map<String, Result<DeviceMessage>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Result<DeviceMessage>>()

        if (parallel) {
            // 并行发送
            val jobs = deviceAddresses.map { address ->
                scope.async {
                    address to sendCommandSync(address, characteristicUuid, data, timeoutMillis)
                }
            }

            jobs.awaitAll().forEach { (address, result) ->
                results[address] = result
            }
        } else {
            // 串行发送
            deviceAddresses.forEach { address ->
                results[address] = sendCommandSync(address, characteristicUuid, data, timeoutMillis)
                delay(50) // 小延迟避免拥堵
            }
        }

        results
    }

    // ------------------------------ 工具方法 ------------------------------

    private fun checkBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isDeviceConnected(address: String): Boolean =
        getConnection(address)?.isConnected() == true

    fun getConnectedDevices(): List<String> =
        connectionPool.filter { it.value.connection.isConnected() }.keys.toList()

    /**
     * 清理空闲连接（连接池管理）
     */
    suspend fun cleanupIdleConnections(idleTimeoutMillis: Long = 300000L): List<String> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val idleConnections = mutableListOf<String>()

            connectionPool.forEach { (address, entry) ->
                if (now - entry.lastActiveTime > idleTimeoutMillis &&
                    !entry.connection.isConnected()
                ) {
                    idleConnections.add(address)
                }
            }

            idleConnections.forEach { address ->
                disconnectDevice(address, removeFromPool = true)
            }

            idleConnections
        }

    /**
     * 释放所有资源
     */
    fun release() {
        scope.launch {
            disconnectAll()
        }
        scope.cancel()
        pendingResponses.clear()
        connectionPool.clear()
        deviceLocks.clear()
    }

    fun requestConnectionPriority(deviceAddress: String, priority: Int): Boolean {
        return getConnection(deviceAddress)?.requestConnectionPriority(priority) ?: false
    }


    // ------------------------------ 数据类 ------------------------------

    /**
     * 设备消息封装
     */
    data class DeviceMessage(
        val deviceAddress: String,
        val deviceName: String,
        val characteristicUuid: UUID,
        val messageId: String?,
        val data: Any?,
        val timestamp: Long,
        val rawBytes: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DeviceMessage

            if (deviceAddress != other.deviceAddress) return false
            if (characteristicUuid != other.characteristicUuid) return false
            if (messageId != other.messageId) return false
            if (timestamp != other.timestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = deviceAddress.hashCode()
            result = 31 * result + characteristicUuid.hashCode()
            result = 31 * result + (messageId?.hashCode() ?: 0)
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }


    private fun getString(@StringRes resId: Int): String {
        val locale = Locale.getDefault()
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config).resources.getString(resId)
    }




    // ------------------------------ 单例 ------------------------------

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: BleConnectionManager? = null

        fun getInstance(context: Context): BleConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: BleConnectionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
