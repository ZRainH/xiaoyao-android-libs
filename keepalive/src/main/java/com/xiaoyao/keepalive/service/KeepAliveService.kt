package com.xiaoyao.keepalive.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.xiaoyao.keepalive.KeepAlive
import com.xiaoyao.keepalive.KeepAliveConfig

/**
 * 保活前台服务：拉起通知后回调已注册的 [com.xiaoyao.keepalive.KeepAliveWorker]。
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"
    }

    private var workersStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val config = KeepAlive.getConfig()
        ensureChannel(config)
        enterForeground(config)
        startWorkers()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = KeepAlive.getConfig()
        ensureChannel(config)
        enterForeground(config)
        if (!workersStarted) {
            startWorkers()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopWorkers()
        KeepAlive.markStopped()
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    private fun enterForeground(config: KeepAliveConfig) {
        val notification = buildNotification(config)
        val targetType = resolveForegroundServiceType(config.foregroundServiceType)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetType != 0) {
                ServiceCompat.startForeground(
                    this,
                    config.notificationId,
                    notification,
                    targetType
                )
            } else {
                startForeground(config.notificationId, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground with targetType ($targetType) failed, fallback to DATA_SYNC", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        config.notificationId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(config.notificationId, notification)
                }
            } catch (fallbackError: Exception) {
                Log.e(TAG, "startForeground fallback failed", fallbackError)
            }
        }
    }

    private fun resolveForegroundServiceType(requestedType: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return 0
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return requestedType
        }
        // Android 14+ (API 34+)
        var resolvedType = requestedType
        val requiresConnectedDevice =
            (resolvedType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) != 0
        if (requiresConnectedDevice && !hasConnectedDevicePrerequisites()) {
            Log.w(
                TAG,
                "FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE requested but prerequisites not met; " +
                    "falling back to FOREGROUND_SERVICE_TYPE_DATA_SYNC"
            )
            resolvedType = (resolvedType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE.inv()) or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        if (resolvedType == 0) {
            resolvedType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return resolvedType
    }

    private fun hasConnectedDevicePrerequisites(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val permissions = mutableListOf(
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        return permissions.any { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensureChannel(config: KeepAliveConfig) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val existing = nm.getNotificationChannel(config.notificationChannelId)
        if (existing != null) return
        val channel = NotificationChannel(
            config.notificationChannelId,
            config.notificationChannelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keep process alive in background"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(config: KeepAliveConfig): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val iconRes = if (config.notificationIcon != 0) {
            config.notificationIcon
        } else {
            android.R.drawable.ic_dialog_info
        }
        return NotificationCompat.Builder(this, config.notificationChannelId)
            .setContentTitle(config.notificationTitle)
            .setContentText(config.notificationText)
            .setSmallIcon(iconRes)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startWorkers() {
        if (workersStarted) return
        workersStarted = true
        KeepAlive.snapshotWorkers().forEach { worker ->
            runCatching { worker.onStart(this) }
                .onFailure { Log.e(TAG, "worker.onStart failed", it) }
        }
    }

    private fun stopWorkers() {
        if (!workersStarted) return
        KeepAlive.snapshotWorkers().forEach { worker ->
            runCatching { worker.onStop() }
                .onFailure { Log.e(TAG, "worker.onStop failed", it) }
        }
        workersStarted = false
    }
}
