# Xiaoyao Android Libraries

本仓库提供可独立引用的 Android 库模块，文档分开维护，请按模块查看使用说明。

| 模块 | 说明 | 坐标 | 文档 |
|------|------|------|------|
| **bluetooth** | 多设备 BLE：连接管理、扫连重连、指令收发与协议解析 | `io.github.zrainh:bluetooth:1.0.0` | [bluetooth/README.md](bluetooth/README.md) |
| **pay** | 支付工具：支付宝、微信、银联 | `io.github.zrainh:pay:1.0.0` | [pay/README.md](pay/README.md) |

仓库需配置 `mavenCentral()`。

```kotlin
dependencies {
    implementation("io.github.zrainh:bluetooth:1.0.0")
    implementation("io.github.zrainh:pay:1.0.0")
}
```
