# Xiaoyao Android Libraries

本仓库提供可独立引用的 Android 库模块，文档分开维护，请按模块查看使用说明。

| 模块 | 说明 | 坐标 | 文档 |
|------|------|------|------|
| **bluetooth** | 多设备 BLE：连接管理、扫连重连、指令收发与协议解析 | `io.github.zrainh:bluetooth:1.0.0` | [bluetooth/README.md](bluetooth/README.md) |
| **pay** | 支付工具：支付宝、微信、银联 | `io.github.zrainh:pay:1.0.0` | [pay/README.md](pay/README.md) |
| **serial** | 原生串口会话（licheedev）+ 多连接/协议/execute | `io.github.zrainh:serial:1.2.0` | [serial/README.md](serial/README.md) |
| **modbus** | Modbus RTU 主站（基于 serial） | `io.github.zrainh:modbus:1.0.0` | [modbus/README.md](modbus/README.md) |

仓库需配置 `mavenCentral()`；`modbus` 依赖 `:serial`。

```kotlin
dependencies {
    implementation("io.github.zrainh:bluetooth:1.0.0")
    implementation("io.github.zrainh:pay:1.0.0")
    implementation("io.github.zrainh:serial:1.2.0")
    implementation("io.github.zrainh:modbus:1.0.0")
}
```
