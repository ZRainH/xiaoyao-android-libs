# Xiaoyao Obfuscation

对外用的 **ProGuard / R8 consumer 规则包**。宿主依赖本库并开启 `minifyEnabled` 后，规则会自动合并进混淆流程。

坐标：`io.github.zrainh:obfuscation:1.0.0`

## 它做什么 / 不做什么

| 做 | 不做 |
|----|------|
| Kotlin / 协程 / AndroidX / Activity·Fragment·View / OkHttp·Retrofit / Gson / Glide / EventBus / Hilt 等通用 keep | 业务 Model 等专用 keep（写在宿主自己的 `proguard-rules.pro`） |
| 未引入对应依赖时用 `-dontwarn` 避免报错 | 替代 `serial` / `keepalive` 等库自己的 consumer 规则 |
| 几乎无运行时代码（仅 `Obfuscation.VERSION` 标记） | Gradle 插件、字符串加密 |

## 接入

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.zrainh:obfuscation:1.0.0")
    // 本地工程：
    // implementation(project(":obfuscation"))
}

android {
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro" // 宿主业务 keep
            )
        }
    }
}
```

确认依赖打进包：

```kotlin
Log.d("obf", Obfuscation.VERSION) // "1.0.0"
```

## 规则文件一览

| 文件 | 内容 |
|------|------|
| `proguard/00-common.pro` | 堆栈、注解等属性 |
| `proguard/10-kotlin.pro` | Kotlin Metadata |
| `proguard/20-coroutines.pro` | kotlinx.coroutines |
| `proguard/30-androidx.pro` | ViewModel / LifecycleObserver |
| `proguard/35-android-ui.pro` | Activity / Fragment / 自定义 View / Adapter / Parcelable / JS 桥 |
| `proguard/40-okhttp-retrofit.pro` | OkHttp、Retrofit |
| `proguard/50-gson.pro` | TypeToken、SerializedName |
| `proguard/60-glide.pro` | GlideModule |
| `proguard/70-eventbus.pro` | `@Subscribe` |
| `proguard/80-hilt.pro` | Hilt / Dagger |

需要裁剪时，可 fork 后删减 `build.gradle.kts` 里的 `consumerProguardFiles(...)` 列表。

## 与本仓库其它库的关系

- `serial` / `modbus` / `keepalive` / `bluetooth` 等：**各自 AAR 已带公开 API 的 consumer 规则**，本包不重复。
- 宿主同时依赖本包 + 业务库即可。

## 发布

```bash
./gradlew :obfuscation:publishReleasePublicationToMavenCentralRepository
```

本地验证 AAR：

```bash
./gradlew :obfuscation:assembleRelease
# 检查 AAR 内 proguard.txt / META-INF/proguard
```
