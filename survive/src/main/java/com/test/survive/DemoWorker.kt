package com.test.survive

import android.app.Service
import android.util.Log
import com.xiaoyao.keepalive.KeepAliveWorker

/**
 * 演示用 Worker：只打日志。
 *
 * 真实项目可在 onStart 里接入蓝牙自动连接，例如：
 * ```
 * // BleAutoConnectManager(...).enable(...)
 * ```
 * 在 onStop 里释放连接与扫描。
 */
class DemoWorker : KeepAliveWorker {
    override fun onStart(service: Service) {
        Log.i(TAG, "DemoWorker.onStart — 在此挂接蓝牙/串口等业务")
    }

    override fun onStop() {
        Log.i(TAG, "DemoWorker.onStop — 释放业务资源")
    }

    companion object {
        private const val TAG = "DemoWorker"
    }
}
