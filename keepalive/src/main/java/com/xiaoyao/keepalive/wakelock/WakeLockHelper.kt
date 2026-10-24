package com.xiaoyao.keepalive.wakelock

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Partial WakeLock 辅助类。仅在短时 CPU 密集任务（OTA、实时采集）时使用，用完必须 [release]。
 * 默认不在 [com.xiaoyao.keepalive.KeepAlive.start] 时持有。
 */
object WakeLockHelper {
    private const val TAG = "WakeLockHelper"
    private const val DEFAULT_TAG = "KeepAlive:Partial"

    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * @param timeoutMs 自动释放超时；<=0 表示不设超时（务必自行 release）
     */
    @Synchronized
    fun acquire(context: Context, tag: String = DEFAULT_TAG, timeoutMs: Long = 60_000L) {
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return
        if (wakeLock?.isHeld == true) {
            Log.d(TAG, "already held")
            return
        }
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
            setReferenceCounted(false)
            if (timeoutMs > 0) {
                acquire(timeoutMs)
            } else {
                acquire()
            }
        }
        Log.d(TAG, "acquired timeoutMs=$timeoutMs")
    }

    @Synchronized
    fun release() {
        val lock = wakeLock ?: return
        try {
            if (lock.isHeld) {
                lock.release()
                Log.d(TAG, "released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "release failed", e)
        } finally {
            wakeLock = null
        }
    }

    fun isHeld(): Boolean = wakeLock?.isHeld == true
}
