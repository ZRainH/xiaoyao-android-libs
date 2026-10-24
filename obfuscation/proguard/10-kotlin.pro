# =============================================================================
# Kotlin
# 保留 Metadata 等运行期信息，避免反射或序列化相关能力被误删。
# =============================================================================

# Kotlin 类元数据（编译器写入），部分库会通过反射读取
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# when 表达式生成的映射表字段，混淆后可能导致分支异常
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# 宿主未完整引入 kotlin stdlib 某些模块时，忽略缺失告警
-dontwarn kotlin.**
