package com.xiaoyao.keepalive

import android.content.pm.ServiceInfo
import androidx.annotation.DrawableRes

/**
 * 前台保活服务类型。
 */
enum class KeepAliveType {
    /**
     * 通用数据同步/网络心跳/定时保活（默认）。
     * 无需任何动态权限（如蓝牙、定位等），开箱即用。
     */
    DATA_SYNC,

    /**
     * 蓝牙/外部硬件连接保活。
     * 适用于需在后台与 BLE/蓝牙外设保持通信的场景；需宿主声明并获得蓝牙权限。
     */
    BLUETOOTH
}

/**
 * 保活配置。
 *
 * @param notificationTitle 前台通知标题
 * @param notificationText 前台通知正文
 * @param notificationChannelId 通知渠道 ID
 * @param notificationChannelName 通知渠道名称
 * @param notificationId 通知 ID
 * @param notificationIcon 通知小图标（建议使用矢量图标，如宿主应用的 ic_launcher_foreground）
 * @param type 保活业务类型，默认为 [KeepAliveType.DATA_SYNC]（无需蓝牙权限）；需要蓝牙时设为 [KeepAliveType.BLUETOOTH]
 * @param foregroundServiceType Android 10+ 前台服务类型，默认由 [type] 自动派生
 * @param requestIgnoreBatteryOnStart 调用 [KeepAlive.start] 时是否尝试跳转电池白名单
 */
data class KeepAliveConfig(
    val notificationTitle: String = "服务运行中",
    val notificationText: String = "保持后台运行",
    val notificationChannelId: String = "keepalive_channel",
    val notificationChannelName: String = "后台保活",
    val notificationId: Int = 0x4B41, // 'KA'
    @DrawableRes val notificationIcon: Int = android.R.drawable.ic_dialog_info,
    val type: KeepAliveType = KeepAliveType.DATA_SYNC,
    val foregroundServiceType: Int = when (type) {
        KeepAliveType.BLUETOOTH -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        KeepAliveType.DATA_SYNC -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    },
    val requestIgnoreBatteryOnStart: Boolean = false
) {
    /**
     * 快捷配置辅助构造函数。
     *
     * @param useBluetooth 是否启用蓝牙外设保活类型。若为 false，则为通用数据同步保活，无需任何蓝牙权限。
     */
    constructor(
        notificationTitle: String = "服务运行中",
        notificationText: String = "保持后台运行",
        notificationChannelId: String = "keepalive_channel",
        notificationChannelName: String = "后台保活",
        notificationId: Int = 0x4B41,
        @DrawableRes notificationIcon: Int = android.R.drawable.ic_dialog_info,
        useBluetooth: Boolean,
        requestIgnoreBatteryOnStart: Boolean = false
    ) : this(
        notificationTitle = notificationTitle,
        notificationText = notificationText,
        notificationChannelId = notificationChannelId,
        notificationChannelName = notificationChannelName,
        notificationId = notificationId,
        notificationIcon = notificationIcon,
        type = if (useBluetooth) KeepAliveType.BLUETOOTH else KeepAliveType.DATA_SYNC,
        requestIgnoreBatteryOnStart = requestIgnoreBatteryOnStart
    )
}
