# =============================================================================
# OkHttp / Retrofit
# 平台注解与 HTTP 接口方法依赖运行期注解；Public Suffix 表按类名加载。
# =============================================================================

# --- OkHttp ---
# 域名公共后缀数据库按类名加载，保留类名即可
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Retrofit ---
# 保留运行期可见注解，否则 @GET / @POST 等会被剥掉导致请求失败
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
# 带 Retrofit HTTP 注解的接口方法：允许压缩与混淆类名，但保留方法签名信息
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn retrofit2.**
