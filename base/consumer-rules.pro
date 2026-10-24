# Hilt / Dagger（宿主 minify 时自动合并）
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclassmembers class * {
    @dagger.hilt.* <methods>;
    @javax.inject.* <fields>;
    @javax.inject.* <init>(...);
}
-dontwarn com.google.errorprone.annotations.**
