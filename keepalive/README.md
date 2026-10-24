# Xiaoyao KeepAlive

通用 Android 后台保活库：提升进程优先级、引导电池白名单与厂商设置，并通过 **Worker** 挂载业务逻辑。

**不包含** 蓝牙扫描/重连、串口协议。这些由业务在 `KeepAliveWorker` 中自行接入。

## 依赖

```kotlin
implementation(project(":keepalive"))
// 或发布后：
// implementation("io.github.zrainh:keepalive:x.y.z")
```

`minSdk` 与工程 catalog 一致（当前 26）。

## 快速开始

```kotlin
// Application.onCreate
KeepAlive.init(
    this,
    KeepAliveConfig(
        notificationTitle = "设备服务运行中",
        notificationText = "保持连接",
        notificationIcon = R.mipmap.ic_launcher,
        foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    )
)

KeepAlive.addWorker(object : KeepAliveWorker {
    override fun onStart(service: Service) {
        // 在此启动蓝牙 / 串口等业务
    }
    override fun onStop() {
        // 释放资源
    }
})

KeepAlive.start()
KeepAlive.stop()
```

## API 一览

| API | 作用 |
|-----|------|
| `KeepAlive.init` | 初始化配置（须在 `start` 前调用） |
| `KeepAlive.addWorker` / `removeWorker` | 挂载 / 移除业务 Worker |
| `KeepAlive.start` / `stop` | 启停前台服务 |
| `KeepAlive.isRunning` | 是否处于运行态 |
| `BatteryOptimizationHelper.requestIgnoreIfNeeded` | 引导忽略电池优化 |
| `WakeLockHelper.acquire` / `release` | 短时 Partial WakeLock（默认不在 start 时持有） |
| `VendorGuide.openAutoStartSettings` | 跳转厂商自启动页 |
| `VendorGuide.openBatteryUnrestricted` | 跳转电池无限制相关页 |
| `VendorRom.displayName` | 当前厂商显示名 |

## 权限（库 Manifest 已声明，合并到宿主）

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` / `FOREGROUND_SERVICE_DATA_SYNC`
- `POST_NOTIFICATIONS`（Android 13+ 需运行时申请）
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `WAKE_LOCK`

Service：`KeepAliveService`，`foregroundServiceType="connectedDevice|dataSync"`。

业务实际使用的 type 须与 `KeepAliveConfig.foregroundServiceType` 一致，否则 Android 14+ 可能抛异常。

## 与 surviveDoc 的对应关系

| 文档能力 | 本库 |
|----------|------|
| 1. 前台服务 | `KeepAlive` + `KeepAliveService` |
| 2. 忽略电池优化 | `BatteryOptimizationHelper` |
| 3. Companion Device Manager | **未做**（第二期） |
| 4. 蓝牙 autoConnect / 退避重连 | **业务侧**（Worker 内接 `:bluetooth`） |
| 5. Partial WakeLock | `WakeLockHelper`（按需短时） |
| 6. 厂商保活引导 | `VendorGuide` / `VendorRom` |

演示工程：`:survive`。

## 能力边界与风险

- 前台服务是合规保活的实际上限；国产 ROM 仍可能杀进程，需配合自启动 / 加锁 / 无限制耗电。
- 直接弹电池白名单对话框可能影响上架审核，建议由 UI 按钮触发。
- 勿长期持有 WakeLock，避免耗电与商店警告。
- 不做双进程、Job/Alarm 拉活等灰色手段。

## 结构

```
keepalive/
  KeepAlive / KeepAliveConfig / KeepAliveWorker
  service/KeepAliveService
  battery/BatteryOptimizationHelper
  wakelock/WakeLockHelper
  vendor/VendorRom / VendorGuide
```
