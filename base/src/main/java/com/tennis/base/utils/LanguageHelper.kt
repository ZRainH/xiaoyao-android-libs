package com.tennis.base.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.text.TextUtils
import androidx.core.content.edit
import java.util.Locale
import java.util.Locale.getDefault
import kotlin.system.exitProcess

object LanguageHelper {

    const val ZH = "zh"   //简体中文
    const val TW = "tw"   //繁体
    const val EN = "en"   //英文


    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_REGION = "app_region" // 新增地区存储键

    /**
     * 将语言规范为 App 支持的 Locale；未匹配时默认英文。
     */
    private fun resolveLocale(language: String, region: String): Locale {
        val lang = language.lowercase(getDefault())
        return when (lang) {
            ZH -> if (region.isNotEmpty()) Locale(ZH, region) else Locale(ZH)
            EN -> Locale.ENGLISH
            else -> Locale.ENGLISH
        }
    }

    /**
     * 保存语言和地区（支持繁体中文等需要地区的语言）
     * @param language 语言代码（如 "zh"）
     * @param region 地区代码（如 "TW" 台湾繁体，"HK" 香港繁体）
     */
    fun saveLanguage(context: Context, language: String, region: String = "") {
        val locale = resolveLocale(language, region)
        val regionToSave = if (locale.language == ZH) {
            region.ifEmpty { Locale.getDefault().country }
        } else {
            ""
        }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putString(KEY_LANGUAGE, locale.language)
                putString(KEY_REGION, regionToSave)
            }
    }

    /**
     * 获取保存的语言和地区；未匹配 zh/en 时默认英文。
     */
    fun getSavedLocale(context: Context): Locale {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_LANGUAGE)) {
            val language = prefs.getString(KEY_LANGUAGE, EN) ?: EN
            val region = prefs.getString(KEY_REGION, "") ?: ""
            return resolveLocale(language, region)
        }
        val system = Locale.getDefault()
        return resolveLocale(system.language, system.country)
    }

    /**
     * 切换语言（支持繁体）并重启应用
     */
    fun changeLanguageAndRestartApp(activity: Activity, language: String, region: String = "") {
        saveLanguage(activity, language, region)
        restartApp(activity)
    }

    /**
     * 切换语言（支持繁体）并重启指定Activity
     */
    fun changeLanguageAndRestartActivity(
        activity: Activity,
        language: String,
        clazz: Class<*>,
        region: String = ""
    ) {
        saveLanguage(activity, language, region)
        val intent = Intent(activity, clazz)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        activity.finish()
    }

    /**
     * 在attachBaseContext中调用，应用语言配置
     */
    fun attachBaseContext(context: Context): Context {
        val locale = getSavedLocale(context)
        return updateContextLocale(context, locale)
    }

    fun restartApp(context: Context) {
        ActivityManagerUtils.getInstance().finishAllActivity()

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            // 彻底清理栈
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }

        // 给系统 50-100ms 处理启动指令，然后彻底自杀，模拟物理退出
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(0)
        }, 100)
    }

    fun getLanguageStr(locale: Locale): String {
        val language = locale.language
        val country = locale.country
        if (country.lowercase(getDefault()) == "tw") {
            return TW
        }
        return language
    }

    fun getLanguageServiceStr(context: Context): String {
        return if (isChinese(context)) "zh-CN" else "en-US"
    }

    fun isChinese(context: Context) = getSavedLocale(context).language == LanguageHelper.ZH

    /**
     * 应用语言到Context，并强制忽略系统字体大小和加粗
     */
    /**
     * 应用语言到Context，并强制忽略系统字体大小和加粗
     */
    private fun updateContextLocale(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)

        // 1. 获取并克隆基础配置
        val resources = context.resources
        val config = Configuration(resources.configuration)

        // 2. 锁定字体缩放和加粗 (强制 1.0)
        config.setLocale(locale)
        config.fontScale = 1.0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            config.fontWeightAdjustment = 0
        }

        // 3. 【核心修复】强制修正当前 Resources 的 DisplayMetrics
        // 必须手动把 scaledDensity 设回 density，否则即便 fontScale 是 1.0，sp 计算仍可能出错
        val dm = resources.displayMetrics
        dm.scaledDensity = dm.density

        // 4. 强制更新当前 Resources 对象（虽然弃用，但在 Application 级别对兼容性至关重要）
        resources.updateConfiguration(config, dm)

        // 5. 同步给全局 Application Context
        val appContext = context.applicationContext
        if (appContext != null && appContext !== context) {
            val appRes = appContext.resources
            val appDm = appRes.displayMetrics
            appDm.scaledDensity = appDm.density
            appRes.updateConfiguration(config, appDm)
        }

        // 6. 创建并返回带修正配置的 Context
        val newContext = context.createConfigurationContext(config)

        // 7. 再次确保新 Context 的资源也是被锁定的
        val newRes = newContext.resources
        newRes.displayMetrics.scaledDensity = newRes.displayMetrics.density

        return newContext
    }

}
