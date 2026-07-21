package cn.xiaoyao.bluetooth.util

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context

/** 将字节数组转换为十六进制字符串（空格分隔） */
fun ByteArray.toHexString(): String =
    joinToString(separator = " ") { String.format("%02X", it) }

/** 检查特征是否支持读操作 */
fun BluetoothGattCharacteristic.supportsRead(): Boolean =
    (properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0

/** 检查特征是否支持写操作 */
fun BluetoothGattCharacteristic.supportsWrite(): Boolean =
    (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0

/** 检查特征是否支持通知 */
fun BluetoothGattCharacteristic.supportsNotification(): Boolean =
    (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0

fun Context.getBluetoothDeviceByMac(mac: String): BluetoothDevice? {
    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter ?: return null
    return try {
        adapter.getRemoteDevice(mac)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * 将 byte 数组转换为 16 进制字符串
 */
fun bytesToHexString(src: ByteArray?): String? {
    val stringBuilder = StringBuilder()
    if (src == null || src.isEmpty()) {
        return null
    }
    for (i in src.indices) {
        val tmp = src[i].toInt() and 0xFF
        val hv = Integer.toHexString(tmp)
        if (hv.length < 2) {
            stringBuilder.append(0)
        }
        stringBuilder.append(hv)
    }
    return stringBuilder.toString()
}
