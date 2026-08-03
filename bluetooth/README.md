# Bluetooth BLE 库

多设备 BLE 库，支持连接管理、扫连自动重连、指令收发与可插拔协议解析。

- **版本**：`1.0.0`
- **坐标**：`io.github.zrainh:bluetooth`
- **包名**：`cn.xiaoyao.bluetooth`

---

## 依赖

```kotlin
dependencies {
    implementation("io.github.zrainh:bluetooth:1.0.0")
}
```

仓库需包含 `mavenCentral()`。

同工程模块依赖：

```kotlin
implementation(project(":bluetooth"))
```

### 权限

模块已声明蓝牙权限，业务侧仍需运行时申请（Android 12+）：

- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- 部分机型扫描还需定位权限 / 系统定位开关

---

## 使用说明

### 初始化

```kotlin
val connectionManager = BleConnectionManager.getInstance(context)
val autoConnectManager = BleAutoConnectManager.create(context)
```

### 配置服务与特征

```kotlin
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
```

### 手动连接

```kotlin
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
        // 已连接
    }
}
```

### 自动连接（多设备推荐）

```kotlin
autoConnectManager.addAutoConnectDevice(
    address = "AA:BB:CC:DD:EE:FF",
    name = "设备A",
    serviceProfiles = profiles,
    priority = 5,
    connectMode = ConnectMode.AUTO_RECONNECT,
    maxRetryCount = 5,
    isPermanentlyEnabled = true
)

lifecycleScope.launch {
    autoConnectManager.autoConnectStates.collect { map ->
        // Disabled / Waiting / Scanning / Connecting / Connected / Failed / Retrying
    }
}
```

进入手动搜索 / 连接页时，让后台扫连让路：

```kotlin
autoConnectManager.pauseAllForManualOperation()
// 退出手动页后再恢复
autoConnectManager.resumeAllFromManualOperation()
```

### 指令收发

```kotlin
lifecycleScope.launch {
    // 同步发送并等待响应（按 messageId 匹配）
    connectionManager.sendCommandSync(
        deviceAddress = address,
        characteristicUuid = writeUuid,
        data = myCommand,
        timeoutMillis = 5000L
    ).onSuccess { message ->
        // message.data / message.rawBytes
    }

    // 无响应写入
    connectionManager.sendCommandSyncNoResponse(
        deviceAddress = address,
        characteristicUuid = writeUuid,
        data = byteArrayOf(0x01, 0x02)
    )
}
```

监听设备主动推送与连接状态：

```kotlin
lifecycleScope.launch {
    connectionManager.devicePushMessages.collect { /* 无匹配 messageId 的 Notify */ }
}
lifecycleScope.launch {
    connectionManager.connectionStates.collect { /* 连接状态变化 */ }
}
```

### 自定义协议

实现 `ServiceInterpreter`，负责组包、解包与 `messageId` 匹配：

```kotlin
class MyServiceInterpreter : ServiceInterpreter {
    override fun generate(data: Any): ByteArray { /* 对象 → 字节 */ }
    override fun parse(raw: ByteArray): Any? { /* 字节 → 对象 */ }
    override fun generateMessageId(data: Any): String? { /* 发送侧 id */ }
    override fun generateMessageIdFromResponse(raw: ByteArray): String? { /* 响应侧 id */ }
}
```

### 断开连接

```kotlin
lifecycleScope.launch {
    connectionManager.disconnectDevice(address)
    // connectionManager.disconnectByTag("racket")
    // connectionManager.disconnectAll()
}
```
