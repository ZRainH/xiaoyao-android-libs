package cn.xiaoyao.bluetooth.autoconnect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import cn.xiaoyao.bluetooth.manager.BleConnectionManager
import cn.xiaoyao.bluetooth.model.BleServiceProfile
import cn.xiaoyao.bluetooth.model.ConnectMode
import cn.xiaoyao.bluetooth.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlin.collections.forEach

/**
 * 蓝牙自动连接管理器
 * 采用 "扫连结合 (Scan to Connect)" 策略，设备不在身边时静默扫描，靠近/开机时秒连。
 * 具备 "排队防连击" 与 "前台让路" 机制，彻底杜绝底层硬件冲突。
 */
@SuppressLint("MissingPermission")
class BleAutoConnectManager(
    private val context: Context,
    private val connectionManager: BleConnectionManager
) {
    private val TAG = "BleAutoConnectManager"

    // 自动连接配置
    private var isAutoConnectEnabled = true

    // 蓝牙组件与扫描器
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner get() = bluetoothAdapter?.bluetoothLeScanner

    // 扫描控制状态
    private var isScanningForAutoConnect = false
    private val scanLock = Mutex()
    private val lastSeenTimes = ConcurrentHashMap<String, Long>() // 防抖记录

    // 🌟 防御1：批处理锁。防止在排队连接期间，后台扫描器被意外唤醒抢占天线
    @Volatile
    private var isBatchConnecting = false

    // 🌟 防御2：全局手动操作屏蔽锁（优先级最高）。防止前台手动操作与后台抢夺天线资源
    @Volatile
    private var isGloballyPausedByManualOperation = false

    // 记录当前底层扫描器正在过滤的 MAC 地址集合，用于动态更新扫描条件
    private var currentlyScanningAddresses = setOf<String>()

    // 设备自动连接状态管理
    private val autoConnectDevices = ConcurrentHashMap<String, AutoConnectDevice>()
    private val autoConnectScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 全局队列处理锁
    private val processLock = Mutex()

    // 连接锁，避免同一设备多次连接
    private val connectionLocks = ConcurrentHashMap<String, Mutex>()

    // 连接请求队列
    private val connectionQueue = ConcurrentHashMap<String, ConnectionRequest>()

    // 自动连接状态流
    private val _autoConnectStates = MutableStateFlow<Map<String, AutoConnectState>>(emptyMap())
    val autoConnectStates: StateFlow<Map<String, AutoConnectState>> = _autoConnectStates.asStateFlow()

    // 全局自动连接开关状态
    private val _isAutoConnectEnabledFlow = MutableStateFlow(isAutoConnectEnabled)
    val isAutoConnectEnabledFlow: StateFlow<Boolean> = _isAutoConnectEnabledFlow.asStateFlow()

    init {
        startConnectionStateMonitoring()
        startAutoConnectProcessor()
    }

    /**
     * 自动连接设备信息
     */
    data class AutoConnectDevice(
        val address: String,
        val name: String,
        val serviceProfiles: List<BleServiceProfile>,
        val priority: Int = 1,
        var lastAttemptTime: Long = 0,
        var attemptCount: Int = 0,
        var isConnecting: Boolean = false,
        var isPaused: Boolean = false,
        val connectMode: ConnectMode = ConnectMode.AUTO_RECONNECT,
        val maxRetryCount: Int = 5,
        val autoConnectDelay: Long = 1000L,
        val isPermanentlyEnabled: Boolean = false
    )

    private data class ConnectionRequest(
        val address: String,
        val priority: Int = 1,
        val scheduledTime: Long = System.currentTimeMillis(),
        var isProcessing: Boolean = false
    )

    sealed class AutoConnectState {
        object Disabled : AutoConnectState()
        object Waiting : AutoConnectState()
        object Scanning : AutoConnectState()
        object Connecting : AutoConnectState()
        object Connected : AutoConnectState()
        object Failed : AutoConnectState()
        data class Retrying(val attempt: Int, val max: Int) : AutoConnectState()

        override fun toString(): String {
            return when (this) {
                is Disabled -> "禁用"
                is Waiting -> "等待中"
                is Scanning -> "寻找设备中"
                is Connecting -> "连接中"
                is Connected -> "已连接"
                is Failed -> "连接失败"
                is Retrying -> "重试($attempt/$max)"
            }
        }
    }

    // ------------------------------ 前台让路机制 ------------------------------

    /**
     * 🌟 API：挂起所有后台自动行为
     * 当进入手动搜索/连接设备的页面时调用。强制停止后台扫描，清空排队任务，把天线让给前台。
     */
    fun pauseAllForManualOperation() {
        if (isGloballyPausedByManualOperation) return
        Log.w(TAG, "🛑 收到前台手动操作指令，后台自动扫描与连接全面挂起退让！")

        isGloballyPausedByManualOperation = true

        // 1. 强行停止当前的后台扫描
        stopAutoConnectScan()

        // 2. 将正在排队的设备状态重置，防止它们在后台继续抢占
        connectionQueue.keys.forEach { address ->
            val device = autoConnectDevices[address]
            if (device != null && !device.isConnecting && !connectionManager.isDeviceConnected(address)) {
                updateDeviceState(address, AutoConnectState.Scanning)
            }
        }

        // 3. 强行中断正在连接的设备，释放 BluetoothGatt
        autoConnectDevices.forEach { (address, device) ->
            if (device.isConnecting) {
                Log.w(TAG, "⚠️ 前台手动操作，强行中断正在连接的后台设备: $address")
                autoConnectScope.launch {
                    connectionManager.disconnectDevice(address, manual = true, removeFromPool = true)
                }
            }
        }

        // 4. 清空排队队列
        connectionQueue.clear()
    }

    /**
     * 🌟 API：恢复后台自动行为
     * 退出手动页面，或手动连接结束后调用。恢复后台的自动扫描与重连逻辑。
     */
    fun resumeAllFromManualOperation() {
        if (!isGloballyPausedByManualOperation) return
        Log.w(TAG, "▶️ 前台手动操作结束，恢复后台自动扫描与连接！")

        isGloballyPausedByManualOperation = false

        // 唤醒后台扫描器和连接处理器
        updateScannerState()
        autoConnectScope.launch { triggerConnectionProcess() }
    }

    // ------------------------------ 核心：扫描模块 ------------------------------

    private val autoConnectScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.address?.let { address -> handleDeviceScanned(address) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result ->
                result.device?.address?.let { address -> handleDeviceScanned(address) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "自动连接后台扫描失败: errorCode=$errorCode")
            isScanningForAutoConnect = false
            currentlyScanningAddresses = emptySet()
            autoConnectScope.launch {
                delay(5000)
                updateScannerState()
            }
        }
    }

    private fun handleDeviceScanned(address: String) {
        // 如果处于全局让路状态，当没看到，绝对不发连接请求
        if (isGloballyPausedByManualOperation) return

        val device = autoConnectDevices[address] ?: return

        if (connectionManager.isDeviceConnected(address) || device.isConnecting || device.isPaused) return

        val now = System.currentTimeMillis()
        val lastSeen = lastSeenTimes[address] ?: 0L
        if (now - lastSeen < 5000) return
        lastSeenTimes[address] = now

        Log.i(TAG, "🎯 扫描到设备广播，准备加入自动连接队列: $address")

        addToConnectionQueue(address, device.priority + 10)
        autoConnectScope.launch { triggerConnectionProcess() }
    }

    private fun updateScannerState() {
        // 🛡️ 拦截：被全局暂停，或者正在集中排队连接中，禁止开启后台扫描！
        if (isGloballyPausedByManualOperation || isBatchConnecting) {
            return
        }

        autoConnectScope.launch {
            scanLock.withLock {
                // 获取锁后再次校验，防止等待锁期间状态改变
                if (isGloballyPausedByManualOperation || isBatchConnecting) return@withLock

                if (!isAutoConnectEnabled || bluetoothAdapter?.isEnabled != true) {
                    stopAutoConnectScan()
                    return@withLock
                }

                val addressesNeedingScan = autoConnectDevices.values
                    .filter { !connectionManager.isDeviceConnected(it.address) && !it.isConnecting && !it.isPaused }
                    .map { it.address }
                    .toSet()

                val needsScanning = addressesNeedingScan.isNotEmpty()

                if (needsScanning) {
                    if (!isScanningForAutoConnect || currentlyScanningAddresses != addressesNeedingScan) {
                        stopAutoConnectScan()
                        delay(200) // 必须延迟，给底层释放资源的时间

                        // 协程延迟后第三次校验，绝对安全
                        if (isGloballyPausedByManualOperation || isBatchConnecting) return@withLock

                        startAutoConnectScan(addressesNeedingScan)
                    }
                } else {
                    if (isScanningForAutoConnect) {
                        stopAutoConnectScan()
                    }
                }
            }
        }
    }

    private fun startAutoConnectScan(addressesToScan: Set<String>) {
        if (isGloballyPausedByManualOperation || isBatchConnecting) return
        try {
            val scanner = bluetoothLeScanner ?: return

            val filters = addressesToScan.map { address ->
                ScanFilter.Builder().setDeviceAddress(address).build()
            }

            if (filters.isEmpty()) return

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()

            scanner.startScan(filters, settings, autoConnectScanCallback)
            isScanningForAutoConnect = true
            currentlyScanningAddresses = addressesToScan

            Log.d(TAG, "🔍 已开启后台均衡扫描，目标: $currentlyScanningAddresses")

            addressesToScan.forEach { address ->
                if (connectionQueue[address] == null) {
                    updateDeviceState(address, AutoConnectState.Scanning)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "开启后台扫描异常", e)
        }
    }

    private fun stopAutoConnectScan() {
        if (!isScanningForAutoConnect) return
        try {
            bluetoothLeScanner?.stopScan(autoConnectScanCallback)
            isScanningForAutoConnect = false
            currentlyScanningAddresses = emptySet()
            Log.d(TAG, "🛑 已停止后台扫描")
        } catch (e: Exception) {
            Log.e(TAG, "停止后台扫描异常", e)
        }
    }

    // ------------------------------ 设备管理 API ------------------------------

    fun setAutoConnectEnabled(enabled: Boolean) {
        isAutoConnectEnabled = enabled
        _isAutoConnectEnabledFlow.value = enabled

        if (!enabled) {
            cancelAllAutoConnections()
        } else {
            updateScannerState()
            autoConnectScope.launch { triggerConnectionProcess() }
        }
    }

    fun isAutoConnectEnabled(): Boolean = isAutoConnectEnabled

    fun addAutoConnectDevice(
        address: String, name: String = "Unknown",
        serviceProfiles: List<BleServiceProfile> = emptyList(),
        priority: Int = 1, connectMode: ConnectMode = ConnectMode.AUTO_RECONNECT,
        maxRetryCount: Int = 5, autoConnectDelay: Long = 1000L,
        isPermanentlyEnabled: Boolean = false
    ) {
        val device = AutoConnectDevice(
            address = address, name = name, serviceProfiles = serviceProfiles,
            priority = priority, connectMode = connectMode,
            maxRetryCount = maxRetryCount, autoConnectDelay = autoConnectDelay,
            isPermanentlyEnabled = isPermanentlyEnabled
        )
        autoConnectDevices[address] = device

        if (isAutoConnectEnabled) {
            // 【修改点】：直接将状态置为“扫描中”，不加入 connectionQueue
            updateDeviceState(address, AutoConnectState.Scanning)
            Log.i(TAG, "📥 插入自动连接设备 $address，准备开启底层扫描，等待设备出现...")

            // 【修改点】：唤醒扫描器。扫描器扫到了自然会走 handleDeviceScanned 去连接
            updateScannerState()
        } else {
            updateDeviceState(address, AutoConnectState.Disabled)
        }
    }

    fun addAutoConnectDevices(devices: List<AutoConnectDevice>) {
        devices.forEach { device ->
            autoConnectDevices[device.address] = device
            if (isAutoConnectEnabled) {
                updateDeviceState(device.address, AutoConnectState.Scanning)
            } else {
                updateDeviceState(device.address, AutoConnectState.Disabled)
            }
        }

        if (isAutoConnectEnabled) {
            Log.i(TAG, "📥 批量插入 ${devices.size} 台设备，准备开启底层扫描...")
            // 【修改点】：统一唤醒扫描器去寻找这些设备，而不是直接强制连接
            updateScannerState()
        }
    }

    private fun addToConnectionQueue(address: String, priority: Int = 1, delayMs: Long = 0) {
        val request = ConnectionRequest(address, priority, System.currentTimeMillis() + delayMs)
        connectionQueue[address] = request
    }

    fun removeAutoConnectDevice(address: String) {
        connectionQueue.remove(address)
        connectionLocks.remove(address)
        autoConnectDevices[address]?.isConnecting = false

        if (connectionManager.isDeviceConnected(address)) {
            autoConnectScope.launch { connectionManager.disconnectDevice(address, manual = true) }
        }

        autoConnectDevices.remove(address)
        updateDeviceState(address, AutoConnectState.Disabled)
        updateScannerState()
    }

    fun removeAutoConnectDevices(addresses: List<String>) {
        addresses.forEach { removeAutoConnectDevice(it) }
    }

    fun clearAllAutoConnectDevices() {
        val addresses = autoConnectDevices.keys.toList()
        addresses.forEach { removeAutoConnectDevice(it) }
    }

    fun getAutoConnectDevices(): List<AutoConnectDevice> =
        autoConnectDevices.values.sortedByDescending { it.priority }

    fun getDeviceAutoConnectState(address: String): AutoConnectState =
        _autoConnectStates.value[address] ?: AutoConnectState.Disabled

    fun isAutoConnectDevice(address: String): Boolean =
        autoConnectDevices.containsKey(address)

    private fun updateDeviceState(address: String, state: AutoConnectState) {
        _autoConnectStates.value = _autoConnectStates.value.toMutableMap().apply { put(address, state) }
    }

    fun cancelAutoConnection(address: String) {
        autoConnectDevices[address]?.isConnecting = false
        connectionQueue.remove(address)
        updateDeviceState(address, AutoConnectState.Scanning)
        updateScannerState()
    }

    fun cancelAllAutoConnections() {
        autoConnectDevices.values.forEach { it.isConnecting = false }
        connectionQueue.clear()
        stopAutoConnectScan()
    }

    // ------------------------------ 执行连接模块 ------------------------------

    private fun startAutoConnectProcessor() {
        autoConnectScope.launch {
            delay(1500)
            updateScannerState()

            while (isActive && isAutoConnectEnabled) {
                try {
                    triggerConnectionProcess()
                    updateScannerState()
                    delay(15000)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    delay(3000)
                }
            }
        }
    }

    /**
     * 🌟 完美排队逻辑：串行处理，防并发，避开前台手动操作
     */
    private suspend fun triggerConnectionProcess() {
        processLock.withLock {
            // 🛡️ 拦截：如果前台要求暂停，绝对不执行自动连接
            if (isGloballyPausedByManualOperation) return

            val devicesToConnect = connectionQueue.values
                .filter { !it.isProcessing && System.currentTimeMillis() >= it.scheduledTime }
                .sortedByDescending { it.priority }
                .mapNotNull { req ->
                    autoConnectDevices[req.address]?.also {
                        if (!connectionManager.isDeviceConnected(it.address) && !it.isPaused) {
                            req.isProcessing = true
                        } else {
                            return@mapNotNull null
                        }
                    }
                }
                .take(3)

            if (devicesToConnect.isEmpty()) return

            Log.d(TAG, "🔗 开始顺序连击 ${devicesToConnect.size} 台设备...")

            // 1. 上锁！强制关闭后台扫描器，把资源腾出给 connectGatt
            isBatchConnecting = true
            stopAutoConnectScan()
            delay(500) // 让底层硬件从高频扫描状态中平息下来

            for (device in devicesToConnect) {
                // 中途如果开关关了，或者前台突然要插手，直接终止排队！
                if (!isAutoConnectEnabled || isGloballyPausedByManualOperation) break

                val mutex = connectionLocks.getOrPut(device.address) { Mutex() }
                mutex.withLock {
                    executeConnection(device)
                }

                // 2. 缓冲期：设备连接完毕强制休息 1.5 秒，避免连续 connect 报错 133
                delay(1500)
            }

            // 3. 释放锁
            isBatchConnecting = false
            updateScannerState() // 若处于 isGloballyPausedByManualOperation，内部会被继续拦截，非常安全
        }
    }

    private suspend fun executeConnection(device: AutoConnectDevice) {
        val address = device.address

        if (connectionManager.isDeviceConnected(address)) {
            markConnectionSuccess(address, device)
            return
        }

        synchronized(device) {
            if (device.isConnecting || device.isPaused) return
            device.isConnecting = true
            device.lastAttemptTime = System.currentTimeMillis()
            device.attemptCount++
        }

        updateDeviceState(address, AutoConnectState.Connecting)
        Log.d(TAG, "🚀 发起连接尝试: ${device.name} ($address)")

        try {
            val result = connectionManager.getBluetoothDeviceByMacNoScan(address)
            if (result.isSuccess && result.getOrNull() != null) {
                val connection = connectionManager.getOrCreateConnection(
                    device = result.getOrNull()!!,
                    connectMode = device.connectMode,
                    serviceProfiles = device.serviceProfiles
                )

                val connectResult = withTimeout(40000) {
                    connection.connectAsync(35000)
                }

                if (connectResult.isSuccess) {
                    markConnectionSuccess(address, device)
                    Log.d(TAG, "✅ 自动连接成功: ${device.name} ($address)")
                } else {
                    handleConnectionFailure(device, connectResult.exceptionOrNull())
                }
            } else {
                handleConnectionFailure(device, Exception("无法获取设备实例"))
            }
        } catch (e: TimeoutCancellationException) {
            handleConnectionFailure(device, TimeoutException("连接超时"))
        } catch (e: Exception) {
            handleConnectionFailure(device, e)
        } finally {
            synchronized(device) { device.isConnecting = false }
            connectionQueue.remove(address)
        }
    }

    private fun markConnectionSuccess(address: String, device: AutoConnectDevice) {
        synchronized(device) {
            device.attemptCount = 0
            device.isConnecting = false
            device.isPaused = false
        }
        connectionQueue.remove(address)
        updateDeviceState(address, AutoConnectState.Connected)
        updateScannerState()
    }

    private fun handleConnectionFailure(device: AutoConnectDevice, exception: Throwable?) {
        val address = device.address
        if (connectionManager.isDeviceConnected(address)) {
            markConnectionSuccess(address, device)
            return
        }

        Log.w(TAG, "❌ 连接失败或超时: $address")

        synchronized(device) {
            device.isConnecting = false
            if (!device.isPaused) {
                updateDeviceState(address, AutoConnectState.Scanning)
                updateScannerState()
            }
        }
    }

    // ------------------------------ 连接状态监听 ------------------------------

    private fun startConnectionStateMonitoring() {
        autoConnectScope.launch {
            var previousStates = emptyMap<String, Any>()

            connectionManager.connectionStates.collect { states ->
                states.forEach { (address, state) ->
                    val device = autoConnectDevices[address] ?: return@forEach
                    val prevState = previousStates[address]
                    if (prevState == state) return@forEach

                    when (state) {
                        is ConnectionState.Connected -> {
                            markConnectionSuccess(address, device)
                        }
                        is ConnectionState.Disconnected, is ConnectionState.Error -> {
                            synchronized(device) { device.isConnecting = false }
                            if (isAutoConnectEnabled && device.connectMode == ConnectMode.AUTO_RECONNECT && !device.isPaused) {
                                val isJustDisconnected = prevState is ConnectionState.Connected || prevState is ConnectionState.Connecting

                                if (isJustDisconnected) {
                                    Log.d(TAG, "⚠️ 设备断开连接，准备恢复扫描: $address")
                                    autoConnectScope.launch {
                                        delay(1500)
                                        if (getDeviceAutoConnectState(address) !is AutoConnectState.Scanning && !device.isPaused) {
                                            updateDeviceState(address, AutoConnectState.Scanning)
                                            updateScannerState()
                                        }
                                    }
                                } else {
                                    if (getDeviceAutoConnectState(address) !is AutoConnectState.Scanning) {
                                        updateDeviceState(address, AutoConnectState.Scanning)
                                        updateScannerState()
                                    }
                                }
                            } else {
                                updateDeviceState(address, AutoConnectState.Disabled)
                            }
                        }
                        is ConnectionState.Connecting -> {
                            synchronized(device) { device.isConnecting = true }
                            updateDeviceState(address, AutoConnectState.Connecting)
                        }
                        else -> {}
                    }
                }
                previousStates = states
            }
        }
    }

    // ------------------------------ 手动控制扩展 ------------------------------

    fun manuallyDisconnectDevice(address: String) {
        val device = autoConnectDevices[address] ?: return
        synchronized(device) {
            device.isPaused = true
            device.isConnecting = false
        }
        connectionQueue.remove(address)
        updateDeviceState(address, AutoConnectState.Disabled)
        autoConnectScope.launch {
            connectionManager.disconnectDevice(address, manual = true)
        }
        updateScannerState()
    }

    suspend fun manuallyConnectDevice(address: String): Boolean {
        val device = autoConnectDevices[address] ?: return false
        synchronized(device) { device.isPaused = false }
        return try {
            val result = connectionManager.getBluetoothDeviceByMacNoScan(address)
            if (result.isSuccess && result.getOrNull() != null) {
                val connection = connectionManager.getOrCreateConnection(
                    device = result.getOrNull()!!,
                    connectMode = ConnectMode.MANUAL,
                    serviceProfiles = device.serviceProfiles
                )
                val connectResult = withTimeout(10000) { connection.connectAsync() }
                connectResult.isSuccess
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun resumeAutoConnect(address: String) {
        val device = autoConnectDevices[address] ?: return
        synchronized(device) { if (!device.isPaused) return; device.isPaused = false }
        updateDeviceState(address, AutoConnectState.Scanning)
        updateScannerState()
    }

    fun connectAllNow() {
        if (!isAutoConnectEnabled) return
        autoConnectScope.launch {
            autoConnectDevices.values
                .filter { !connectionManager.isDeviceConnected(it.address) && !it.isPaused }
                .forEach { device -> addToConnectionQueue(device.address, device.priority) }
            triggerConnectionProcess()
        }
    }

    fun getStatistics(): AutoConnectStatistics {
        val totalDevices = autoConnectDevices.size
        val connectingDevices = autoConnectDevices.values.count { it.isConnecting }
        val connectedDevices = autoConnectDevices.values.count { connectionManager.isDeviceConnected(it.address) }
        val failedDevices = autoConnectStates.value.values.count { it is AutoConnectState.Failed }
        val queuedDevices = connectionQueue.size

        return AutoConnectStatistics(
            totalDevices = totalDevices,
            connectingDevices = connectingDevices,
            connectedDevices = connectedDevices,
            failedDevices = failedDevices,
            queuedDevices = queuedDevices,
            isEnabled = isAutoConnectEnabled,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    data class AutoConnectStatistics(
        val totalDevices: Int,
        val connectingDevices: Int,
        val connectedDevices: Int,
        val failedDevices: Int,
        val queuedDevices: Int,
        val isEnabled: Boolean,
        val lastUpdateTime: Long
    )

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: BleAutoConnectManager? = null

        fun create(context: Context): BleAutoConnectManager {
            val connectionManager = BleConnectionManager.getInstance(context)
            return instance ?: synchronized(this) {
                instance ?: BleAutoConnectManager(context.applicationContext, connectionManager)
            }.also { instance = it }
        }
    }
}