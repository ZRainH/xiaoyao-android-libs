# =============================================================================
# EventBus
# @Subscribe 方法按方法名查找，混淆改名会导致收不到事件。
# =============================================================================

-keepattributes *Annotation*

# 保留所有带 @Subscribe 的方法（订阅回调）
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}

# 线程模式枚举
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

-dontwarn org.greenrobot.eventbus.**
