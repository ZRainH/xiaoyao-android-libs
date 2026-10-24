package com.xiaoyao.keepalive

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.xiaoyao.keepalive.service.KeepAliveService
import com.xiaoyao.keepalive.wakelock.WakeLockHelper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 后台保活门面：前台服务 + Worker 挂载。
 *
 * 用法：
 * ```
 * KeepAlive.init(app, KeepAliveConfig(...))
 * KeepAlive.addWorker(myWorker)
 * KeepAlive.start()
 * KeepAlive.stop()
 * ```
 */
object KeepAlive {
    private const val TAG = "KeepAlive"

    @Volatile
    private var app: Application? = null

    @Volatile
    private var config: KeepAliveConfig = KeepAliveConfig()

    private val workers = CopyOnWriteArrayList<KeepAliveWorker>()
    private val running = AtomicBoolean(false)

    fun init(application: Application, config: KeepAliveConfig = KeepAliveConfig()) {
        app = application
        this.config = config
        Log.d(TAG, "init channel=${config.notificationChannelId}")
    }

    fun getConfig(): KeepAliveConfig = config

    fun isRunning(): Boolean = running.get()

    fun addWorker(worker: KeepAliveWorker) {
        if (!workers.contains(worker)) {
            workers.add(worker)
        }
    }

    fun removeWorker(worker: KeepAliveWorker) {
        workers.remove(worker)
    }

    fun clearWorkers() {
        workers.clear()
    }

    internal fun snapshotWorkers(): List<KeepAliveWorker> = workers.toList()

    /**
     * 启动前台保活服务。须先 [init]。
     */
    fun start(context: Context? = null) {
        val ctx = context?.applicationContext ?: app
        if (ctx == null) {
            Log.e(TAG, "start ignored: call init() first")
            return
        }
        val intent = Intent(ctx, KeepAliveService::class.java)
        ContextCompat.startForegroundService(ctx, intent)
        running.set(true)
        Log.d(TAG, "start requested")
    }

    /**
     * 停止前台服务，并释放 [WakeLockHelper] 持有的锁。
     */
    fun stop(context: Context? = null) {
        val ctx = context?.applicationContext ?: app
        if (ctx == null) {
            Log.e(TAG, "stop ignored: call init() first")
            return
        }
        ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        WakeLockHelper.release()
        running.set(false)
        Log.d(TAG, "stop requested")
    }

    internal fun markStopped() {
        running.set(false)
    }

    internal fun requireApp(): Application =
        app ?: error("KeepAlive.init() must be called before starting the service")
}
