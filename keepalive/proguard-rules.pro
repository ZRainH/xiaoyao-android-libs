# Xiaoyao KeepAlive — consumer ProGuard rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep public class com.xiaoyao.keepalive.KeepAlive {
    public *;
}
-keep public class com.xiaoyao.keepalive.KeepAliveConfig {
    public *;
}
-keep public interface com.xiaoyao.keepalive.KeepAliveWorker {
    *;
}
-keep class * implements com.xiaoyao.keepalive.KeepAliveWorker {
    *;
}
-keep public class com.xiaoyao.keepalive.service.KeepAliveService {
    public *;
}
-keep public class com.xiaoyao.keepalive.battery.** {
    public *;
}
-keep public class com.xiaoyao.keepalive.wakelock.** {
    public *;
}
-keep public class com.xiaoyao.keepalive.vendor.** {
    public *;
}
