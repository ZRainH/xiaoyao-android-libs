package com.test.survive

import android.app.Application
import com.xiaoyao.keepalive.KeepAlive
import com.xiaoyao.keepalive.KeepAliveConfig
import com.xiaoyao.keepalive.KeepAliveType

class SurviveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KeepAlive.init(
            this,
            KeepAliveConfig(
                notificationTitle = "Survive 保活演示",
                notificationText = "前台服务运行中",
                notificationIcon = R.drawable.ic_launcher_foreground,
                // 需要蓝牙时配置: type = KeepAliveType.BLUETOOTH (或 useBluetooth = true)
                // 不需要蓝牙时配置: type = KeepAliveType.DATA_SYNC (默认值，无需任何蓝牙权限)
                type = KeepAliveType.DATA_SYNC
            )
        )
        // 业务逻辑挂在 Worker 里；库本身不依赖 bluetooth / serial
        KeepAlive.addWorker(DemoWorker())
    }
}
