package cn.xiaoyao.bluetooth.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import cn.xiaoyao.bluetooth.model.BleScanResult
import cn.xiaoyao.bluetooth.util.BlePermissions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 通用 BLE 设备扫描器
 * 支持按 Flow 实时推送去重设备列表、按 RSSI 排序、名称/UUID 过滤、超时自动停止与扫描状态监听。
 */
class BleScanner private constructor(private val context: Context) {
    private val TAG = "BleScanner"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /**
     * 开启通用蓝牙扫描 Flow
     *
     * @param timeoutMillis 扫描超时时间（毫秒），默认 10000ms（10秒）。<= 0 表示不限时（需手动 cancel 协程）
     * @param serviceUuids 过滤包含指定 GATT 服务 UUID 的设备
     * @param nameFilter 按设备名称过滤关键词（忽略大小写）
     * @param macFilter 指定扫描的 MAC 地址列表
     * @param scanMode 扫描模式（默认低延迟 SCAN_MODE_LOW_LATENCY）
     * @return 实时去重并按 RSSI 从强到弱排序的设备列表 Flow
     */
    @SuppressLint("MissingPermission")
    fun startScanFlow(
        timeoutMillis: Long = 10000L,
        serviceUuids: List<UUID>? = null,
        nameFilter: String? = null,
        macFilter: List<String>? = null,
        scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY
    ): Flow<List<BleScanResult>> = callbackFlow {
        if (!BlePermissions.hasScanPermission(context)) {
            Log.e(TAG, "缺少 BLUETOOTH_SCAN 权限，无法开启扫描")
            close(SecurityException("Missing BLUETOOTH_SCAN permission"))
            return@callbackFlow
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "蓝牙未开启，取消扫描")
            close(IllegalStateException("Bluetooth is disabled"))
            return@callbackFlow
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "无法获取 BluetoothLeScanner")
            close(IllegalStateException("BluetoothLeScanner unavailable"))
            return@callbackFlow
        }

        val deviceMap = ConcurrentHashMap<String, BleScanResult>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result == null) return

                val device = result.device
                val address = device.address ?: return

                // 优先从 scanRecord 拿设备名（很多 BLE 设备广播包里的名字比 device.name 更准确）
                val recordName = result.scanRecord?.deviceName
                val deviceName = recordName
                    ?: try { device.name } catch (e: SecurityException) { null }
                    ?: "Unknown"

                // 名字过滤
                if (!nameFilter.isNullOrBlank()) {
                    if (!deviceName.contains(nameFilter, ignoreCase = true)) {
                        return
                    }
                }

                // MAC 过滤
                if (!macFilter.isNullOrEmpty()) {
                    if (!macFilter.any { it.equals(address, ignoreCase = true) }) {
                        return
                    }
                }

                val scanDevice = BleScanResult(
                    device = device,
                    name = deviceName,
                    address = address,
                    rssi = result.rssi,
                    scanResult = result,
                    timestamp = System.currentTimeMillis()
                )

                deviceMap[address] = scanDevice

                // 实时推送按 RSSI 排序后的列表
                val sortedList = deviceMap.values.sortedByDescending { it.rssi }
                trySend(sortedList)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "扫描失败: errorCode = $errorCode")
                _isScanning.value = false
                close(IllegalStateException("Scan failed with error code: $errorCode"))
            }
        }

        // 构建 ScanFilter
        val filters = mutableListOf<ScanFilter>()
        serviceUuids?.forEach { uuid ->
            filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build())
        }
        macFilter?.forEach { mac ->
            filters.add(ScanFilter.Builder().setDeviceAddress(mac).build())
        }

        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()

        _isScanning.value = true
        Log.i(TAG, "🔍 开始通用 BLE 设备扫描...")

        try {
            scanner.startScan(if (filters.isNotEmpty()) filters else null, settings, callback)
        } catch (e: Exception) {
            Log.e(TAG, "startScan 发生异常", e)
            _isScanning.value = false
            close(e)
            return@callbackFlow
        }

        // 超时控制
        val timeoutJob = if (timeoutMillis > 0) {
            launch {
                delay(timeoutMillis)
                Log.d(TAG, "⏰ 扫描超时 (${timeoutMillis}ms)，自动停止")
                channel.close()
            }
        } else null

        awaitClose {
            Log.i(TAG, "🛑 停止通用 BLE 扫描")
            timeoutJob?.cancel()
            _isScanning.value = false
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {
                Log.e(TAG, "stopScan 发生异常", e)
            }
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: BleScanner? = null

        fun getInstance(context: Context): BleScanner {
            return instance ?: synchronized(this) {
                instance ?: BleScanner(context.applicationContext).also { instance = it }
            }
        }
    }
}
