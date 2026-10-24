package com.tennis.base.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.AppUtils.getAppVersionName
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * @Author : 逍遥
 * @Date : on 2026/3/30.
 * @Description : App 崩溃与异常重启守护管理器
 */
object AppGuardManager {

    var isNormalStart = false
    private var lastCrashTime: Long = 0
    // 增加一个变量，用来保存上层模块传进来的 Class
    private var targetActivityClass: Class<out Activity>? = null

    /**
     * 初始化全局崩溃拦截 (不需要再传 Activity Class 了)
     */
    fun initCrashHandler(application: Application, targetClass: Class<out Activity>? = null) {
        this.targetActivityClass = targetClass
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e("AppGuardManager", "捕获到全局异常，准备重启 App", exception)

            // 1. 🌟 新增：将崩溃信息保存到本地文件
            saveCrashLogToFile(application.applicationContext, exception)

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCrashTime < 3000) {
                defaultHandler?.uncaughtException(thread, exception)
                killCurrentProcess()
                return@setDefaultUncaughtExceptionHandler
            }
            lastCrashTime = currentTime

            // 直接调用动态重启
            restartApp(application.applicationContext)
            killCurrentProcess()
        }
    }

    /**
     * 检查并处理僵尸重启 (不需要再传 Activity Class 了)
     */
    fun checkZombieRestart(activity: Activity, savedInstanceState: Bundle?): Boolean {
        if (savedInstanceState != null && !isNormalStart) {
            Log.w("AppGuardManager", "检测到 App 在后台被回收，正在清理残余页面并重启")
            restartApp(activity)
            activity.finish()
            return true
        }
        return false
    }

    /**
     * 🌟 核心修改点：动态获取 App 的启动页 Intent，实现模块解耦
     */
    private fun restartApp(context: Context) {
        targetActivityClass?.let { clazz ->
            val intent = Intent(context, clazz).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    private fun killCurrentProcess() {
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }

    /**
     * 🌟 核心新增功能：收集设备信息并保存崩溃日志到本地
     */
    private fun saveCrashLogToFile(context: Context, exception: Throwable) {
        try {
            // 1. 获取完整的错误堆栈信息
            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            exception.printStackTrace(printWriter)
            var cause = exception.cause
            while (cause != null) {
                cause.printStackTrace(printWriter)
                cause = cause.cause
            }
            printWriter.close()
            val crashInfo = stringWriter.toString()

            // 2. 收集设备基础信息
            val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timeString = timeFormatter.format(Date())

            val deviceInfo = StringBuilder().apply {
                append("--- 崩溃日志 ---\n")
                append("崩溃时间: $timeString\n")
                append("手机型号: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("安卓版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                append("App 版本: ${getAppVersionName()}\n")
                append("-----------------\n\n")
                append("详细堆栈:\n")
                append(crashInfo)
            }.toString()

            // 3. 确定保存目录 (不需要存储权限)
            // 路径: /storage/emulated/0/Android/data/你的包名/files/CrashLogs/
            val logDir = context.getExternalFilesDir("CrashLogs") ?: context.filesDir
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            // 4. 以时间命名文件并写入
            val fileNameFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "Crash_${fileNameFormatter.format(Date())}.txt"
            val logFile = File(logDir, fileName)

            logFile.writeText(deviceInfo)

            Log.e("AppGuardManager", "崩溃日志已保存至: ${logFile.absolutePath}")

        } catch (e: Exception) {
            // 如果写日志本身抛出异常，只打印，不要影响后续的重启流程
            Log.e("AppGuardManager", "保存崩溃日志失败", e)
        }
    }
}