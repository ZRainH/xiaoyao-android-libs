package cn.xiaoyao.bluetooth.model

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult

/**
 * 通用 BLE 设备扫描结果数据模型
 */
data class BleScanResult(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val scanResult: ScanResult,
    val timestamp: Long = System.currentTimeMillis()
)
