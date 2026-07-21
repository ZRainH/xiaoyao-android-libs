# AndroidBluetooth

多设备 BLE 库（`cn.xiaoyao.bluetooth`），支持连接管理、扫连自动重连、指令收发与可插拔协议解析。

- **最新版本**：`1.0.0`
- **Maven 坐标**：`cn.xiaoyao:bluetooth:1.0.0`
- **命名空间**：`cn.xiaoyao.bluetooth`
- **Release**：https://github.com/ZRainH/AndroidBluetooth/releases/tag/v1.0.0
- **详细文档**：[bluetooth/README.md](bluetooth/README.md)

---

## 快速接入（推荐：GitHub Packages）

### 1. 配置仓库

在目标工程的 `settings.gradle.kts` 中：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/ZRainH/AndroidBluetooth")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

在用户目录 `%USERPROFILE%\.gradle\gradle.properties` 中配置（**不要提交到 Git**）：

```properties
gpr.user=你的GitHub用户名
gpr.key=ghp_xxxxxxxxxxxxxxxxxxxx
```

Token 至少需要 `read:packages`；若仓库为私有，还需 `repo`。

### 2. 添加依赖

```kotlin
dependencies {
    implementation("cn.xiaoyao:bluetooth:1.0.0")
}
```

### 3. 申请权限

模块已声明蓝牙权限，业务侧仍需运行时申请（Android 12+）：

- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- 部分机型扫描还需定位权限 / 系统定位开关

---

## 其他接入方式

### 下载 Release AAR

1. 打开：https://github.com/ZRainH/AndroidBluetooth/releases  
2. 下载 `bluetooth-release.aar` 到工程 `libs/`  
3. 依赖：

```kotlin
dependencies {
    implementation(files("libs/bluetooth-release.aar"))
}
```

> 直接引用 AAR 时，需自行补充库的传递依赖（如 `androidx.core:core-ktx`、`lifecycle` 等）。更推荐用 GitHub Packages。

### 同工程模块依赖

```kotlin
// settings.gradle.kts
include(":bluetooth")

// app/build.gradle.kts
implementation(project(":bluetooth"))
```

---

## 快速开始

```kotlin
val connectionManager = BleConnectionManager.getInstance(context)
val autoConnectManager = BleAutoConnectManager.create(context)

// 配置服务 / 特征后连接
val profiles = listOf(
    BleServiceProfile(
        serviceUuid = serviceUuid,
        characteristics = listOf(
            BleCharacteristicProfile(writeUuid),
            BleCharacteristicProfile(notifyUuid, enableNotification = true)
        ),
        serviceInterpreter = MyServiceInterpreter()
    )
)

lifecycleScope.launch {
    val device = connectionManager
        .getBluetoothDeviceByMacNoScan("AA:BB:CC:DD:EE:FF")
        .getOrNull() ?: return@launch

    val connection = connectionManager.getOrCreateConnection(
        device = device,
        connectMode = ConnectMode.MANUAL,
        serviceProfiles = profiles
    )

    if (connection.connectAsync().isSuccess) {
        // 发送指令
        connectionManager.sendCommandSync(
            deviceAddress = device.address,
            characteristicUuid = writeUuid,
            data = myCommand
        )
    }
}
```

自动连接（多设备常驻）：

```kotlin
autoConnectManager.addAutoConnectDevice(
    address = "AA:BB:CC:DD:EE:FF",
    name = "设备A",
    serviceProfiles = profiles,
    connectMode = ConnectMode.AUTO_RECONNECT
)

lifecycleScope.launch {
    autoConnectManager.autoConnectStates.collect { /* Scanning / Connecting / Connected */ }
}
```

进入手动配网页时，记得让后台扫连让路：

```kotlin
autoConnectManager.pauseAllForManualOperation()
// ... 手动扫描 / 连接 ...
autoConnectManager.resumeAllFromManualOperation()
```

---

## 模块结构

| 包 | 职责 |
|----|------|
| `autoconnect` | 扫连结合、自动重连、前台让路 |
| `manager` | 多设备连接池、扫描、指令收发 |
| `connection` | 单设备 GATT |
| `protocol` | 组包 / 解包 / messageId |
| `model` | 状态与配置模型 |
| `exception` | 异常与错误码 |
| `util` | 工具扩展 |

---

## 发布维护（作者）

```powershell
# 1. 修改版本号：bluetooth/build.gradle.kts 中 bluetoothVersion
# 2. 发布到 GitHub Packages
.\gradlew :bluetooth:publishReleasePublicationToGitHubPackagesRepository

# 3. 打包 AAR 并创建 Release
.\gradlew :bluetooth:assembleRelease
git tag v1.0.1
git push origin v1.0.1
gh release create v1.0.1 `
  "bluetooth\build\outputs\aar\bluetooth-release.aar#bluetooth-release.aar" `
  --title "v1.0.1" `
  --notes "更新说明"
```

---

## 更多文档

完整 API、协议自定义、常见问题与排错说明见：

**[bluetooth/README.md](bluetooth/README.md)**
