# =============================================================================
# Kotlin Coroutines
# 防止 R8 删掉调度器工厂 / 异常处理器等 SPI 与易变字段。
# =============================================================================

# 主线程调度器工厂：按名称查找，需保留类名
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
# 全局协程异常处理器：按名称查找
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 协程内部大量使用 volatile 字段做状态机，不能被优化掉
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# 宿主未使用协程时，忽略相关类缺失告警
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.atomicfu.**
