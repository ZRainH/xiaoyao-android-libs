package com.xiaoyao.keepalive.vendor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.xiaoyao.keepalive.battery.BatteryOptimizationHelper

/**
 * 厂商自启动 / 后台无限制设置页跳转。
 * Intent 因 ROM 版本差异较大，失败时回退到应用详情页。
 */
object VendorGuide {
    private const val TAG = "VendorGuide"

    /** 尝试打开自启动管理页 */
    fun openAutoStartSettings(context: Context): Boolean {
        val intents = autoStartIntents(context)
        return launchFirst(context, intents) || BatteryOptimizationHelper.openAppDetails(context)
    }

    /** 尝试打开电池「无限制 / 不优化」相关页 */
    fun openBatteryUnrestricted(context: Context): Boolean {
        val intents = batteryUnrestrictedIntents(context)
        return launchFirst(context, intents)
            || BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
            || BatteryOptimizationHelper.openAppDetails(context)
    }

    /** 打开应用详情（多任务加锁等需用户手动操作，此处仅作兜底入口） */
    fun openAppDetails(context: Context): Boolean =
        BatteryOptimizationHelper.openAppDetails(context)

    private fun autoStartIntents(context: Context): List<Intent> {
        val pkg = context.packageName
        return when (VendorRom.brand()) {
            VendorBrand.XIAOMI -> listOf(
                componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ),
                componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                )
            )
            VendorBrand.HUAWEI, VendorBrand.HONOR -> listOf(
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                ),
                componentIntent(
                    "com.hihonor.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            )
            VendorBrand.OPPO, VendorBrand.ONEPLUS -> listOf(
                componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                componentIntent(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                ),
                componentIntent(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.startupapp.view.StartupAppListActivity"
                )
            )
            VendorBrand.VIVO -> listOf(
                componentIntent(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                ),
                componentIntent(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            )
            VendorBrand.MEIZU -> listOf(
                componentIntent(
                    "com.meizu.safe",
                    "com.meizu.safe.permission.SmartBGActivity"
                )
            )
            VendorBrand.SAMSUNG -> listOf(
                componentIntent(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            )
            VendorBrand.OTHER -> emptyList()
        }.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .plus(packageDetailsIntent(pkg))
    }

    private fun batteryUnrestrictedIntents(context: Context): List<Intent> {
        val pkg = context.packageName
        val list = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            list += Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
            }
            list += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        when (VendorRom.brand()) {
            VendorBrand.XIAOMI -> list += componentIntent(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            ).apply {
                putExtra("package_name", pkg)
                putExtra("package_label", appLabel(context))
            }
            VendorBrand.HUAWEI, VendorBrand.HONOR -> list += componentIntent(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
            else -> Unit
        }
        return list.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun launchFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "launched ${intent.component ?: intent.action}")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "launch failed: ${intent.component ?: intent.action}", e)
            }
        }
        return false
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    private fun packageDetailsIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun appLabel(context: Context): String =
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(context.packageName, 0)).toString()
        }.getOrDefault(context.packageName)
}
