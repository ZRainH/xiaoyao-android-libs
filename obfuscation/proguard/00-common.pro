# =============================================================================
# 通用属性 / 崩溃堆栈
# 宿主开启 minify 后自动合并；不依赖具体第三方库，所有 App 都适用。
# =============================================================================

# 保留源文件名与行号，便于线上崩溃还原到具体代码行
-keepattributes SourceFile,LineNumberTable
# 混淆后堆栈里的文件名统一替换，避免泄露真实路径，同时仍能配合 mapping 还原
-renamesourcefileattribute SourceFile
# 注解、泛型签名、内部类、异常表：反射 / 序列化 / 调试依赖这些属性
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
