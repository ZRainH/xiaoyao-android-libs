package cn.xiaoyao.bluetooth.connection

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import cn.xiaoyao.bluetooth.model.BleCharacteristicProfile
import cn.xiaoyao.bluetooth.model.BleServiceProfile
import cn.xiaoyao.bluetooth.model.ConnectMode
import cn.xiaoyao.bluetooth.model.ConnectionState
import cn.xiaoyao.bluetooth.protocol.ParserRegistry
import cn.xiaoyao.bluetooth.util.bytesToHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * 蓝牙设备连接管理类
 */
class BleConnection(
    private val context: Context,
    val bluetoothDevice: BluetoothDevice,
    private var connectMode: ConnectMode = ConnectMode.MANUAL,
    val serviceProfiles: List<BleServiceProfile> = emptyList()
) {
    // 日志标签 - 使用const val
    private companion object {
        const val TAG = "BleDeviceConnection"
        const val DEFAULT_MTU = 500
        const val MAX_RETRY_ATTEMPTS = 2
        const val RETRY_DELAY_MS = 2000L
        private val CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val TIMEOUT = 15 * 1000L

        // 添加连接稳定性相关常量
        const val CONNECTION_STABLE_DELAY_MS = 200L
        const val NOTIFICATION_STEP_DELAY_MS = 50L
        const val QUIET_DISABLE_DELAY_MS = 50L
    }

    // 存储已发现的服务
    private val discoveredServices = mutableMapOf<UUID, BluetoothGattService>()

    // 存储已发现的特征
    private val discoveredCharacteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()

    private val discoveredCharacteristicProfiles = mutableListOf<BleCharacteristicProfile>()

    // 协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    // 添加Handler用于简单的延迟操作
    private val handler = Handler(Looper.getMainLooper())

    // GATT客户端
    private var gatt: BluetoothGatt? = null

    // 服务解释器映射
    private val serviceInterpreters = mutableMapOf<UUID, BleServiceProfile>()

    // 状态管理 - 使用LiveData和私有setter
    private val _connectionState = MutableLiveData<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: LiveData<ConnectionState> = _connectionState

    // 蓝牙相关变量 - 使用可空类型
    @Volatile
    private var bluetoothGatt: BluetoothGatt? = null
    private var currentRetryAttempt = 0
    private var connectTimeoutJob: Job? = null
    private val gattCallback = createGattCallback()

    // 用于处理描述符写入结果的挂起延续
    private var connectResultContinuation: Continuation<Result<Unit>>? = null

    // 特征数据通道
    private val _characteristicData = Channel<Pair<UUID, Any?>>(Channel.BUFFERED)
    val characteristicData = _characteristicData.receiveAsFlow()

    // 原子标记：确保延续只被恢复一次
    private val isConnectionResolved = AtomicBoolean(false)

    // 移除自动重连相关属性
    private var isManualDisconnect = false

    // 添加通知重试相关属性
    private var notificationRetryJob: Job? = null
    private var notificationRetryCount = 0

    // 添加GATT错误处理相关属性
    private var lastGattErrorTime = 0L
    private var gattErrorCount = 0

    // 并发安全的操作锁和状态管理
    private val operationMutex = Mutex()
    private val activeCharacteristics = ConcurrentHashMap<UUID, Boolean>() // UUID -> 是否是Indication
    private val pendingOperations = ConcurrentHashMap<UUID, CompletableDeferred<Boolean>>()

    // 并发安全的操作锁和状态管理
    private val writeMutex = Mutex() // 专用于写入特征排队
    private val connectMutex = Mutex() //专用于防止并发发起连接

    // 静态初始化解析器
    init {
        ParserRegistry.initializeDefaultParsers()
    }

    /**
     * 检查设备是否已连接
     */
    fun isConnected(): Boolean {
        val state = _connectionState.value
        return state == ConnectionState.Connected
    }

    /**
     * 安全地恢复连接挂起函数（保证全局只执行一次）
     */
    private fun resolveConnectionContinuation(result: Result<Unit>) {
        if (isConnectionResolved.compareAndSet(false, true)) {
            // 取消超时定时器
            connectTimeoutJob?.cancel()

            val continuation = connectResultContinuation
            connectResultContinuation = null

            if (continuation?.context?.isActive == true) {
                continuation.resume(result)
            } else {
                Log.w(TAG, "协程已取消或结束，拦截并放弃本次 resume")
            }
        }
    }

    /**
     * 连接
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connectAsync(timeoutMs: Long = 35000): Result<Unit> = connectMutex.withLock {

        val timeSinceDisconnect = System.currentTimeMillis() - lastDisconnectTime
        if (timeSinceDisconnect in 1..1500) {
            val waitTime = 1500 - timeSinceDisconnect
            Log.w(TAG, "距离上一次断开太近，挂起等待 ${waitTime}ms，确保旧的 GATT 彻底释放...")
            delay(waitTime)
        }

        if (isConnected()) {
            return Result.success(Unit)
        }

        return withContext(Dispatchers.Main) {

            suspendCancellableCoroutine { continuation ->
                isConnectionResolved.set(false)
                connectResultContinuation = continuation

                // 清理旧资源
                cleanupGattResources()

                // 【修复：自动重连失败】因为我们已经在外部实现了后台高频扫描机制。
                // 只有在扫描到设备时才会发起 connectAsync，此时设备必然在附近，应该使用直接连接 (autoConnect=false)
                // 如果使用 true，Android 系统会将其放入低优先级的后台连接队列，导致连接缓慢甚至连不上。
                val isAutoConnect = false
                Log.i(TAG, "主线程发起底层 GATT 连接 -> ${bluetoothDevice.address}, 禁用系统autoConnect=$isAutoConnect")

                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    bluetoothDevice.connectGatt(
                        context,
                        isAutoConnect,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    bluetoothDevice.connectGatt(
                        context,
                        isAutoConnect,
                        gattCallback
                    )
                }

                if (gatt == null) {
                    val error = "Failed to create GATT connection (System rejected)"
                    _connectionState.postValue(ConnectionState.Error(error))
                    resolveConnectionContinuation(Result.failure(IllegalStateException(error)))
                    return@suspendCancellableCoroutine
                }

                bluetoothGatt = gatt
                _connectionState.postValue(ConnectionState.Connecting)

                // 【修复核心1】第一阶段：物理建立连接的超时 (仅到 STATE_CONNECTED)
                // 如果是自动重连模式（autoConnect=true），系统会在后台无限期等待广播，不需要也不能加这个硬性超时
                if (!isAutoConnect) {
                    connectTimeoutJob = coroutineScope.launch {
                        delay(timeoutMs)
                        Log.e(TAG, "🚨 第一阶段底层物理连接超时触发 (${timeoutMs}ms) 强制释放资源")
                        _connectionState.postValue(ConnectionState.Error("Connection Timeout"))
                        cleanupGattResources()
                        resolveConnectionContinuation(Result.failure(TimeoutException("Physical Connection Timeout")))
                    }
                }

                continuation.invokeOnCancellation {
                    Log.w(TAG, "connectAsync 协程被外部强制取消")
                    resolveConnectionContinuation(Result.failure(CancellationException("Coroutine Cancelled")))
                    _connectionState.postValue(ConnectionState.Disconnected)
                    cleanupGattResources()
                }
            }
        }
    }

    /**
     * 安全关闭GATT连接
     */
    private fun safeCloseGatt(gatt: BluetoothGatt?) {
        if (gatt == null) return
        try {
            // 发送断开物理层指令
            gatt.disconnect()

            // 绝对不能立刻 close()！必须给底层硬件处理断开指令的缓冲时间。
            // 使用 GlobalScope 防止随组件销毁而被取消，从而导致底层 GATT 泄露
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                delay(800) // 必须延迟 600ms ~ 1000ms
                try {
                    gatt.close()
                    Log.d(TAG, "GATT 彻底释放并关闭 -> ${gatt.device.address}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing GATT", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting GATT", e)
        }
    }


    /**
     * 清理GATT资源
     */
    private fun cleanupGattResources() {
        serviceProfiles.forEach { profile ->
            profile.serviceInterpreter.release()
        }

        connectTimeoutJob?.cancel()
        notificationRetryJob?.cancel()

        // 先将当前的 gatt 对象赋给局部变量，然后再交给 safeCloseGatt 异步慢慢关
        // 这样不会阻塞 App 层的业务逻辑，同时又能安全清理硬件资源
        val gattToClose = bluetoothGatt
        bluetoothGatt = null
        safeCloseGatt(gattToClose)

        discoveredServices.clear()
        discoveredCharacteristics.clear()
        discoveredCharacteristicProfiles.clear()
        serviceInterpreters.clear()
        activeCharacteristics.clear()

        // 极其重要：强制取消等待写入回调的协程，防止后续流程死锁卡住
        pendingOperations.forEach { (_, operation) ->
            operation.cancel()
        }
        pendingOperations.clear()
    }

    private var lastDisconnectTime: Long = 0

    /**
     * 断开连接 - 优化为同步释放
     */
    fun disconnect(manual: Boolean = true) {
        lastDisconnectTime = System.currentTimeMillis()
        isManualDisconnect = manual
        resolveConnectionContinuation(Result.failure(CancellationException("Manual disconnect")))

        serviceProfiles.forEach { profile ->
            profile.serviceInterpreter.release()
        }

        cleanupGattResources()

        isConnectionResolved.set(false)
        connectResultContinuation = null

        _connectionState.postValue(ConnectionState.Disconnected)

        handler.postDelayed({
            _connectionState.postValue(ConnectionState.IDEL)
        }, 100)
    }

    /**
     * 启用/禁用特征 Notify
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Result<Unit> {
        val gatt = bluetoothGatt
            ?: return Result.failure(IllegalStateException("Not connected to GATT"))

        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
            Log.e(TAG, "特征不支持NOTIFY通知")
            return Result.failure(IllegalStateException("Not NOTIFY"))
        }

        if (!gatt.setCharacteristicNotification(characteristic, enable)) {
            Log.e(TAG, "setCharacteristicNotification失败")
            return Result.failure(IllegalStateException("Failed to set notification"))
        }

        if (!enable) {
            return Result.success(Unit)
        }

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            ?: return Result.success(Unit)

        return try {
            val success = writeDescriptorAndAwait(gatt, characteristic, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (success) Result.success(Unit)
            else Result.failure(IllegalStateException("Failed to write descriptor"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 启用/禁用特征 Indication
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun setCharacteristicIndication(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Result<Unit> {
        val gatt = bluetoothGatt
            ?: return Result.failure(IllegalStateException("Not connected to GATT"))

        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0) {
            Log.e(TAG, "特征不支持INDICATE通知")
            return Result.failure(IllegalStateException("Not INDICATE"))
        }

        if (!gatt.setCharacteristicNotification(characteristic, enable)) {
            return Result.failure(IllegalStateException("Failed to set notification"))
        }

        if (!enable) {
            return Result.success(Unit)
        }

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            ?: return Result.failure(IllegalStateException("CCCD descriptor not found"))

        return try {
            val success = writeDescriptorAndAwait(gatt, characteristic, descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            if (success) Result.success(Unit)
            else Result.failure(IllegalStateException("Failed to write descriptor"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun writeDescriptorAndAwait(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean = operationMutex.withLock {
        val uuid = characteristic.uuid
        val operation = CompletableDeferred<Boolean>()
        pendingOperations[uuid] = operation

        val commandSent = writeDescriptorWithRetry(gatt, descriptor, value)
        if (!commandSent) {
            pendingOperations.remove(uuid)
            return@withLock false
        }

        return@withLock try {
            withTimeout(2000L) {
                operation.await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "等待描述符写入回调超时: $uuid", e)
            pendingOperations.remove(uuid)
            false
        }
    }

    private fun createGattCallback(): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.i(TAG, "Connection state changed: $newState, status: $status, isManualDisconnect: $isManualDisconnect")

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "✅ 蓝牙物理连接成功，准备发现服务")
                        bluetoothGatt = gatt

                        // 【修复核心2】物理连接成功，必须取消第一阶段超时，开启第二阶段初始化超时！
                        connectTimeoutJob?.cancel()
                        connectTimeoutJob = coroutineScope.launch {
                            delay(15000L) // 给服务发现和通知开启 15 秒的充裕时间
                            Log.e(TAG, "🚨 第二阶段服务发现及初始化超时 (15000ms)，强行释放资源")
                            handleConnectionFailure(gatt, "Initialization Timeout")
                        }

                        coroutineScope.launch {
                            delay(600L)
                            if (_connectionState.value == ConnectionState.Disconnected) return@launch
                            Log.d(TAG, "开始发现服务")
                            gatt.discoverServices()
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.w(TAG, "⚠️ 底层回调断开连接")

                        // 【修复核心3】如果是在漫长的初始化期间意外断开，必须第一时间阻断通道并通知外部
                        if (!isConnectionResolved.get()) {
                            resolveConnectionContinuation(Result.failure(IllegalStateException("Device disconnected midway (status: $status)")))
                        }

                        _connectionState.postValue(ConnectionState.Disconnected)
                        cleanupGattResources()
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                coroutineScope.launch {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "MTU 协商成功，当前 MTU = $mtu")
                    } else {
                        Log.w(TAG, "MTU 协商失败 (status: $status)，将以默认小包模式运行")
                    }
                    gatt?.let { continueWithPhyAndNotifications(it) }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                coroutineScope.launch {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (processDiscoveredServices(gatt)) {
                            Log.i(TAG, "服务发现成功，准备协商 MTU -> ${gatt.device.address}")
                            delay(CONNECTION_STABLE_DELAY_MS)
                            val mtuRequested = gatt.requestMtu(DEFAULT_MTU)
                            if (!mtuRequested) {
                                Log.w(TAG, "设备不支持请求 MTU 或 Android 拒绝请求，触发降级流程")
                                continueWithPhyAndNotifications(gatt)
                            }
                        } else {
                            handleConnectionFailure(gatt, "Required services or characteristics not found")
                        }
                    } else {
                        handleConnectionFailure(gatt, "Failed to discover services, status: $status")
                    }
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                val characteristicUuid = descriptor.characteristic.uuid
                val operation = pendingOperations.remove(characteristicUuid) ?: return
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Descriptor written successfully for ${descriptor.characteristic.uuid}")
                    operation.complete(true)
                } else {
                    val error = "Failed to write descriptor, status: $status"
                    Log.e(TAG, error)
                    operation.completeExceptionally(IllegalStateException(error))
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val rawValue = characteristic.value?.copyOf() ?: return
                coroutineScope.launch {
                    val service = findServiceForCharacteristic(characteristic)
                    service?.let { serviceProfile ->
                        val interpreter = serviceInterpreters[serviceProfile.uuid]
                        interpreter?.serviceInterpreter?.interpret(rawValue, characteristic, interpreter)
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                Log.i(
                    TAG,
                    "发送: ${bytesToHexString(characteristic?.value)} -> 发送结果：$status"
                )
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            private suspend fun continueWithPhyAndNotifications(gatt: BluetoothGatt) {
                bluetoothGatt = gatt

                // 【修复核心4】在正式开始逐个打开特征通知前，彻底关闭总超时定时器。因为特征打开方法自带单步超时。
                connectTimeoutJob?.cancel()

                Log.d(TAG, "准备设置 2M PHY (高速物理层)")
                try {
                    gatt.setPreferredPhy(
                        BluetoothDevice.PHY_LE_2M,
                        BluetoothDevice.PHY_LE_2M,
                        BluetoothDevice.PHY_OPTION_NO_PREFERRED
                    )
                    delay(100L)
                } catch (e: Exception) {
                    Log.w(TAG, "设备不支持 PHY 设置或发生异常，忽略。")
                }

                Log.d(TAG, "准备逐步开启特征通知")
                enableMultipleCharacteristicsGradually(gatt, discoveredCharacteristicProfiles)

                // 【修复核心5】杜绝假死！经过上面漫长的开启过程，必须复查设备是否已经在此期间物理掉线
                if (bluetoothGatt == null || _connectionState.value == ConnectionState.Disconnected) {
                    Log.e(TAG, "🚨 严重拦截：开启特征通知期间，设备物理断开，中断成功回调")
                    resolveConnectionContinuation(Result.failure(IllegalStateException("Device disconnected while enabling characteristics")))
                    return
                }

                Log.i(TAG, "✅ 蓝牙通道已打通，初始化全部完成: ${gatt.device.address}")
                _connectionState.postValue(ConnectionState.Connected)
                resolveConnectionContinuation(Result.success(Unit))
            }

            private fun handleConnectionFailure(gatt: BluetoothGatt?, error: String) {
                Log.e(TAG, error)
                _connectionState.postValue(ConnectionState.Error(error))
                cleanupGattResources()
                resolveConnectionContinuation(Result.failure(IllegalStateException(error)))
                _connectionState.postValue(ConnectionState.Disconnected)
                handler.postDelayed({ _connectionState.postValue(ConnectionState.IDEL) }, 100)
            }

            private fun isAbnormalDisconnection(status: Int): Boolean = status != BluetoothGatt.GATT_SUCCESS

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            private fun processDiscoveredServices(gatt: BluetoothGatt): Boolean {
                discoveredServices.clear()
                discoveredCharacteristics.clear()
                discoveredCharacteristicProfiles.clear()

                return serviceProfiles.all { serviceProfile ->
                    val service = gatt.getService(serviceProfile.serviceUuid)
                    if (service == null) {
                        !serviceProfile.required
                    } else {
                        discoveredServices[serviceProfile.serviceUuid] = service
                        serviceInterpreters[serviceProfile.serviceUuid] = serviceProfile

                        val interpreter = serviceProfile.serviceInterpreter
                        interpreter.setOnDataParsedListener { parsedUuid, parsedData ->
                            Log.i(TAG, "收到完整数据: ${parsedData.toString()}")
                            coroutineScope.launch {
                                _characteristicData.send(Pair(parsedUuid, parsedData))
                            }
                        }

                        serviceProfile.characteristics.all { charProfile ->
                            val characteristic = service.getCharacteristic(charProfile.characteristicUuid)
                            if (characteristic == null) {
                                !charProfile.required
                            } else {
                                characteristic.writeType = charProfile.writeType
                                discoveredCharacteristics[charProfile.characteristicUuid] = characteristic
                                discoveredCharacteristicProfiles.add(charProfile)
                                true
                            }
                        }
                    }
                }
            }
        }
    }

    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeCharacteristic(
        characteristicUuid: UUID,
        data: Any,
        writeType: Int
    ): Boolean {
        val characteristic = discoveredCharacteristics[characteristicUuid] ?: return false
        val serviceProfile = findServiceProfileForCharacteristic(characteristicUuid) ?: return false

        return try {
            val bytes = serviceProfile.serviceInterpreter.generate(characteristicUuid, data)
            characteristic.value = bytes
            characteristic.writeType = writeType
            bluetoothGatt?.writeCharacteristic(characteristic) ?: return false
        } catch (e: Exception) {
            Log.e("BleConnection", "写入特征数据失败: ${e.message}", e)
            false
        }
    }

    fun findServiceProfileForCharacteristic(characteristicUuid: UUID): BleServiceProfile? {
        return serviceProfiles.find { serviceProfile ->
            serviceProfile.characteristics.any {
                it.characteristicUuid == characteristicUuid
            }
        }
    }

    private fun findServiceForCharacteristic(characteristic: BluetoothGattCharacteristic): BluetoothGattService? {
        return discoveredServices.values.firstOrNull { service ->
            service.characteristics.contains(characteristic)
        }
    }

    /**
     * 批量开启多个特征监听 (外部调用方法)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun enableMultipleCharacteristics(
        characteristics: List<BleCharacteristicProfile>
    ): Boolean {
        val gatt = bluetoothGatt
        if (gatt == null) {
            val state = _connectionState.value
            Log.w(TAG, "❌ [操作被拒绝] 设备未连接，无法开启通知。(底层GATT已置空，当前状态: $state)")
            return false
        }
        enableMultipleCharacteristicsGradually(gatt, characteristics)
        return true
    }

    /**
     * 逐步开启多个特征通知 - 内部安全调用 (传入确切的gatt对象)
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun enableMultipleCharacteristicsGradually(
        gatt: BluetoothGatt,
        characteristics: List<BleCharacteristicProfile>
    ) {
        val indicateChars = characteristics.filter { it.enableIndication }
        val notifyChars = characteristics.filter { it.enableNotification }

        Log.d(TAG, "逐步开启特征通知 - Indication: ${indicateChars.size}, Notification: ${notifyChars.size}")

        for ((index, characteristic) in indicateChars.withIndex()) {
            try {
                discoveredCharacteristics[characteristic.characteristicUuid]?.apply {
                    if (index > 0) delay(NOTIFICATION_STEP_DELAY_MS)
                    val success = enableSingleCharacteristic(gatt, this, true)
                    if (success) {
                        activeCharacteristics[characteristic.characteristicUuid] = true
                        Log.d(TAG, "Indication 开启成功: ${characteristic.characteristicUuid}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "开启 Indication 特征 ${characteristic.characteristicUuid} 失败", e)
            }
        }

        for ((index, characteristic) in notifyChars.withIndex()) {
            try {
                discoveredCharacteristics[characteristic.characteristicUuid]?.apply {
                    if (index > 0) delay(NOTIFICATION_STEP_DELAY_MS)
                    val success = enableSingleCharacteristic(gatt, this, false)
                    if (success) {
                        activeCharacteristics[characteristic.characteristicUuid] = false
                        Log.d(TAG, "Notification 开启成功: ${characteristic.characteristicUuid}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "开启 Notification 特征 ${characteristic.characteristicUuid} 失败", e)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun enableSingleCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        isIndicate: Boolean
    ): Boolean = operationMutex.withLock {
        val uuid = characteristic.uuid

        val requiredProperty = if (isIndicate) {
            BluetoothGattCharacteristic.PROPERTY_INDICATE
        } else {
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
        }

        if ((characteristic.properties and requiredProperty) == 0) {
            Log.w(TAG, "特征 $uuid 不支持对应的通知类型")
            return@withLock false
        }

        val notifySuccess = gatt.setCharacteristicNotification(characteristic, true)
        if (!notifySuccess) {
            Log.e(TAG, "本地 setCharacteristicNotification 失败: $uuid")
            return@withLock false
        }

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG) ?: return@withLock false
        val value = if (isIndicate) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        val operation = CompletableDeferred<Boolean>()
        pendingOperations[uuid] = operation

        val commandSent = writeDescriptorWithRetry(gatt, descriptor, value)
        if (!commandSent) {
            pendingOperations.remove(uuid)
            return@withLock false
        }

        return@withLock try {
            withTimeout(2000L) {
                operation.await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "等待描述符写入回调超时: $uuid")
            pendingOperations.remove(uuid)
            false
        }
    }

    private suspend fun writeDescriptorWithRetry(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
        maxRetries: Int = 3
    ): Boolean {
        repeat(maxRetries) { attempt ->
            descriptor.value = value
            val success = gatt.writeDescriptor(descriptor)

            if (success) {
                return true
            }

            delay(50L)
        }
        return false
    }

    fun release() {
        cleanupGattResources()
        coroutineScope.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun requestConnectionPriority(priority: Int): Boolean {
        return bluetoothGatt?.requestConnectionPriority(priority) ?: false
    }

}