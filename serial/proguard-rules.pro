# Xiaoyao Serial — 随 AAR 下发的 consumer 规则（宿主 App 开启 minify 时自动合并）

# 堆栈可读
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- 公开 API ---
-keep public class com.xiaoyao.serial.SerialPortManager {
    public *;
}
-keep public class com.xiaoyao.serial.connection.SerialConnection {
    public *;
}

-keep public interface com.xiaoyao.serial.transport.SerialTransport {
    *;
}
-keep public class com.xiaoyao.serial.transport.** {
    public *;
}

-keep public interface com.xiaoyao.serial.protocol.SerialProtocolAdapter {
    *;
}
-keep public class com.xiaoyao.serial.protocol.** {
    public *;
}
# 业务侧自定义协议适配器（如 WxProtocolAdapter）
-keep class * implements com.xiaoyao.serial.protocol.SerialProtocolAdapter {
    *;
}

-keep public class com.xiaoyao.serial.model.** {
    public *;
}
-keepclassmembers enum com.xiaoyao.serial.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep public class com.xiaoyao.serial.exception.** {
    *;
}

# --- 传递依赖：licheedev android-serialport（JNI / native）---
-keep class android.serialport.** { *; }
-dontwarn android.serialport.**

# Coroutines（库内部 suspend / Flow，宿主混淆时避免误删）
-dontwarn kotlinx.coroutines.**
