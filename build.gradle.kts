// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}

// 宿主 application 模块自动生成微信 WXPayEntryActivity（仅依赖支付的真实宿主）
subprojects {
    pluginManager.withPlugin("com.android.application") {
        // survive / hitdemo 等演示模块不依赖 :pay，跳过以免生成无法编译的 wxapi
        if (project.name == "survive" || project.name == "hitdemo") return@withPlugin
        apply(from = rootProject.file("pay/wxentry.gradle"))
    }
}
