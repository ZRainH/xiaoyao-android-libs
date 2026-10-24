# =============================================================================
# Gson
# TypeToken / SerializedName 依赖反射；业务 Model 的 keep 请写在宿主 proguard-rules.pro。
# =============================================================================

# 泛型签名与注解：TypeToken、字段映射需要
-keepattributes Signature
-keepattributes *Annotation*

# TypeToken 及其匿名子类（new TypeToken<List<Foo>>() {}）
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# 带 @SerializedName 的字段在混淆后仍能按注解名读写 JSON
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-dontwarn com.google.gson.**
-dontwarn sun.misc.**
