package com.xiaoyao.keepalive

import android.app.Service

/**
 * 可挂载到前台服务的业务 Worker。
 * 库只负责保活；蓝牙 / 串口等逻辑由实现类自行接入。
 */
interface KeepAliveWorker {
    /** 前台服务已启动并进入前台态后回调 */
    fun onStart(service: Service)

    /** 前台服务销毁前回调，用于释放资源 */
    fun onStop()
}
