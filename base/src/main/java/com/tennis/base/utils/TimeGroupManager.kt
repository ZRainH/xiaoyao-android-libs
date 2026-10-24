package com.bright.stars.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*

object TimeGroupManager {

    private const val ONE_MINUTE = 60 * 1000L
    private const val ONE_HOUR = 60 * ONE_MINUTE
    private const val ONE_DAY = 24 * ONE_HOUR

    /**
     * 将时间戳转换为分钟级别的key（用于分组）
     * 格式：yyyy-MM-dd HH:mm
     */
    @SuppressLint("DefaultLocale")
    fun Long.toMinuteKey(): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = this
        return String.format(
            "%04d-%02d-%02d %02d:%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1, // 月份从0开始，所以要+1
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }

    /**
     * 将时间戳转换为小时级别的key（用于按小时分组）
     * 格式：yyyy-MM-dd HH
     */
    fun Long.toHourKey(): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = this
        return String.format(
            "%04d-%02d-%02d %02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY)
        )
    }

    /**
     * 将时间戳转换为天级别的key（用于按天分组）
     * 格式：yyyy-MM-dd
     */
    fun Long.toDayKey(): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = this
        return String.format(
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * 解析时间字符串为时间戳
     * 支持格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd HH:mm
     */
    fun parseTimeString(timeString: String): Long {
        return try {
            // 尝试解析 yyyy-MM-dd HH:mm:ss 格式
            try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                format.parse(timeString)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                // 尝试解析 yyyy-MM-dd HH:mm 格式
                try {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    format.parse(timeString)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * 将分钟key解析为时间戳
     * 格式：yyyy-MM-dd HH:mm
     */
    fun parseMinuteKeyToTimestamp(minuteKey: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            format.parse(minuteKey)?.time ?: 0
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 将小时key解析为时间戳
     * 格式：yyyy-MM-dd HH
     */
    fun parseHourKeyToTimestamp(hourKey: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH", Locale.getDefault())
            format.parse(hourKey)?.time ?: 0
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 将天key解析为时间戳
     * 格式：yyyy-MM-dd
     */
    fun parseDayKeyToTimestamp(dayKey: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            format.parse(dayKey)?.time ?: 0
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 获取友好的时间显示
     * 规则：
     * - 今天：显示"今天 HH:mm"
     * - 昨天：显示"昨天 HH:mm"
     * - 前天：显示"前天 HH:mm"
     * - 今年：显示"MM-dd HH:mm"
     * - 更早：显示"yyyy-MM-dd HH:mm"
     */
    fun getFriendlyTimeDisplay(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val today = getDayStart(now)
        val yesterday = today - ONE_DAY
        val dayBeforeYesterday = yesterday - ONE_DAY

        // 获取年份
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val year = calendar.get(Calendar.YEAR)

        calendar.timeInMillis = now
        val currentYear = calendar.get(Calendar.YEAR)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFormat.format(Date(timestamp))

        return when {
            timestamp >= today -> "今天 $timeStr"
            timestamp >= yesterday -> "昨天 $timeStr"
            timestamp >= dayBeforeYesterday -> "前天 $timeStr"
            year == currentYear -> {
                val monthDayFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                monthDayFormat.format(Date(timestamp))
            }
            else -> {
                val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                fullFormat.format(Date(timestamp))
            }
        }
    }

    /**
     * 获取友好的日期显示（只显示日期，不显示时间）
     * 规则：
     * - 今天：显示"今天"
     * - 昨天：显示"昨天"
     * - 前天：显示"前天"
     * - 今年：显示"MM-dd"
     * - 更早：显示"yyyy-MM-dd"
     */
    fun getFriendlyDateDisplay(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val today = getDayStart(now)
        val yesterday = today - ONE_DAY
        val dayBeforeYesterday = yesterday - ONE_DAY

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val year = calendar.get(Calendar.YEAR)

        calendar.timeInMillis = now
        val currentYear = calendar.get(Calendar.YEAR)

        return when {
            timestamp >= today -> "今天"
            timestamp >= yesterday -> "昨天"
            timestamp >= dayBeforeYesterday -> "前天"
            year == currentYear -> {
                val monthDayFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
                monthDayFormat.format(Date(timestamp))
            }
            else -> {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }

    /**
     * 判断两个时间是否在同一天
     */
    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 判断两个时间是否在同一分钟
     */
    fun isSameMinute(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.HOUR_OF_DAY) == cal2.get(Calendar.HOUR_OF_DAY) &&
                cal1.get(Calendar.MINUTE) == cal2.get(Calendar.MINUTE)
    }

    /**
     * 获取指定时间当天的开始时间（00:00:00）
     */
    fun getDayStart(time: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * 获取指定时间当天的结束时间（23:59:59）
     */
    fun getDayEnd(time: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    /**
     * 格式化时间
     * @param timestamp 时间戳
     * @param pattern 格式模式，如 "yyyy-MM-dd HH:mm:ss"
     */
    fun formatTime(timestamp: Long, pattern: String): String {
        return try {
            val format = SimpleDateFormat(pattern, Locale.getDefault())
            format.format(Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 获取当前时间的时间字符串
     * @param pattern 格式模式，如 "yyyy-MM-dd HH:mm:ss"
     */
    fun getCurrentTimeString(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
        return formatTime(System.currentTimeMillis(), pattern)
    }

    /**
     * 计算两个时间的时间差描述
     * @param timestamp1 第一个时间戳
     * @param timestamp2 第二个时间戳
     * @return 时间差描述，如"刚刚"、"5分钟前"、"2小时前"、"3天前"
     */
    fun getTimeDiffDescription(timestamp1: Long, timestamp2: Long = System.currentTimeMillis()): String {
        val diff = timestamp2 - timestamp1

        return when {
            diff < ONE_MINUTE -> "刚刚"
            diff < ONE_HOUR -> "${diff / ONE_MINUTE}分钟前"
            diff < ONE_DAY -> "${diff / ONE_HOUR}小时前"
            diff < 7 * ONE_DAY -> "${diff / ONE_DAY}天前"
            else -> formatTime(timestamp1, "MM-dd")
        }
    }
}