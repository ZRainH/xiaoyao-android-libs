package cn.xiaoyao.bluetooth.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * BLE 运行时权限（按系统版本区分）
 *
 * - Android 12+：BLUETOOTH_SCAN、BLUETOOTH_CONNECT
 * - Android 11 及以下：BLUETOOTH/BLUETOOTH_ADMIN 为安装时权限，扫描还需要 ACCESS_FINE_LOCATION
 */
object BlePermissions {

    /** 需要动态申请的权限 */
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasConnectPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            isGranted(context, Manifest.permission.BLUETOOTH_CONNECT)

    fun hasScanPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isGranted(context, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasAllPermissions(context: Context): Boolean =
        requiredPermissions().all { isGranted(context, it) }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
