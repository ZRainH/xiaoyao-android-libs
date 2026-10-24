# =============================================================================
# AndroidX Lifecycle / ViewModel
# 框架通过反射创建 ViewModel，需保留无参 / Application 构造。
# Activity / Fragment / View 见 35-android-ui.pro。
# =============================================================================

# ViewModelProvider 反射调用无参构造
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# AndroidViewModel 需要 Application 参数的构造
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# LifecycleObserver 实现类的构造（注解式观察者会反射创建）
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    <init>(...);
}

-dontwarn androidx.lifecycle.**
-dontwarn androidx.fragment.app.**
