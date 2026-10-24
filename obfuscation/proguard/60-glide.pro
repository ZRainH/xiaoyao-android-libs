# =============================================================================
# Glide
# AppGlideModule / GlideModule 通过清单与生成类发现，需保留实现类。
# =============================================================================

# 旧版 GlideModule 实现
-keep public class * implements com.bumptech.glide.module.GlideModule

# AppGlideModule（含 GeneratedAppGlideModule）需可反射构造
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}

# 图片头解析枚举的 VALUES 数组
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ParcelFileDescriptor 回绕内部类方法
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

-dontwarn com.bumptech.glide.**
