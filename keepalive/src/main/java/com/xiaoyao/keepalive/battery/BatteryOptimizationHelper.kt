package com.xiaoyao.keepalive.battery

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 电池优化白名单引导。
 *
 * 注意：直接弹 [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] 在部分应用市场上架可能被问询，
 * 建议由 UI 按钮触发，而非静默强制弹出。
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptHelper"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 若尚未豁免，跳转系统对话框请求忽略电池优化。
     * @return true 表示已发起跳转；false 表示已在白名单或无需处理
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreIfNeeded(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) {
            Log.d(TAG, "already ignoring battery optimizations")
            return false
        }
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "request ignore failed, open app details", e)
            openAppDetails(context)
            false
        }
    }

    /** 打开系统电池优化列表（用户自行搜索本应用） */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "open battery settings failed", e)
            openAppDetails(context)
        }
    }

    fun openAppDetails(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "open app details failed", e)
            false
        }
    }
}
