// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

// 宿主 application 模块自动生成微信 WXPayEntryActivity
subprojects {
    pluginManager.withPlugin("com.android.application") {
        apply(from = rootProject.file("pay/wxentry.gradle"))
    }
}
