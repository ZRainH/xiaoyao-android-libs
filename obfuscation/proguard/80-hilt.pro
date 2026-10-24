# =============================================================================
# Hilt / Dagger
# 注解驱动注入；未使用 :base 的宿主也可直接依赖本规则包。
# =============================================================================

# Hilt / javax.inject 运行时类
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Fragment 上下文包装类（Hilt 内部使用）
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# 带 Hilt / Inject 注解的成员与构造
-keepclassmembers class * {
    @dagger.hilt.* <methods>;
    @javax.inject.* <fields>;
    @javax.inject.* <init>(...);
}

-dontwarn com.google.errorprone.annotations.**
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**
