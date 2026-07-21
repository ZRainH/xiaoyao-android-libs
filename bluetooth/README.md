# Bluetooth 模块使用文档

多设备 BLE 库，支持连接管理、扫连自动重连、指令收发与可插拔协议解析。

- **模块名**：`:bluetooth`
- **命名空间**：`cn.xiaoyao.bluetooth`
- **作者**：逍遥

---

## 1. 接入

### 1.1 依赖

**方式 A：Maven Central（推荐，无需 Token）**

```kotlin
dependencies {
    implementation("io.github.zrainh:bluetooth:1.0.0")
}
```

工程已配置 `mavenCentral()` 即可。首次发布后同步可能需要 10–30 分钟。

**方式 B：模块依赖（同工程开发）**

在 App 的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":bluetooth"))
}
```

并确保 `settings.gradle.kts` 已包含：

```kotlin
include(":bluetooth")
```

**方式 C：本地 Maven 仓库**

```powershell
.\gradlew :bluetooth:publishReleasePublicationToLocalRepository
```

然后：

```kotlin
implementation("io.github.zrainh:bluetooth:1.0.0")
```

**方式 D：直接引用 AAR**

```powershell
.\gradlew :bluetooth:assembleRelease
```

将 `bluetooth/build/outputs/aar/bluetooth-release.aar` 复制到目标工程 `libs/`：

```kotlin
implementation(files("libs/bluetooth-release.aar"))
```

**方式 E：GitHub Packages（需登录）**

发布：

```powershell
.\gradlew :bluetooth:publishReleasePublicationToGitHubPackagesRepository
```

引用时需配置带凭证的仓库：`https://maven.pkg.github.com/ZRainH/AndroidBluetooth`  
（详见根目录 [README.md](../README.md)）

### 1.2 权限

模块已声明以下权限，业务侧仍需在运行时申请（尤其 Android 12+）：

| 权限 | 用途 |
|------|------|
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | 旧版蓝牙控制 |
| `BLUETOOTH_SCAN` | 扫描 |
| `BLUETOOTH_CONNECT` | 连接与读写 |

建议同时申请定位相关权限（部分机型扫描需要）。

### 1.3 包结构

| 包 | 职责 |
|----|------|
| `cn.xiaoyao.bluetooth.autoconnect` | 扫连结合、自动重连、前台让路 |
| `cn.xiaoyao.bluetooth.manager` | 多设备连接池、扫描、指令收发 |
| `cn.xiaoyao.bluetooth.connection` | 单设备 GATT |
| `cn.xiaoyao.bluetooth.protocol` | 组包 / 解包 / messageId |
| `cn.xiaoyao.bluetooth.model` | 状态与配置模型 |
| `cn.xiaoyao.bluetooth.exception` | 异常与错误码 |
| `cn.xiaoyao.bluetooth.util` | 工具扩展 |

---

## 2. 快速开始

### 2.1 初始化

```kotlin
val connectionManager = BleConnectionManager.getInstance(context)
val autoConnectManager = BleAutoConnectManager.create(context)
```

### 2.2 配置服务与特征

连接前先声明设备的 GATT 服务 / 特征，以及协议解释器：

```kotlin
val writeUuid = UUID.fromString("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
val notifyUuid = UUID.fromString("yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy")
val serviceUuid = UUID.fromString("zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz")

val profiles = listOf(
    BleServiceProfile(
        serviceUuid = serviceUuid,
        required = true,
        characteristics = listOf(
            BleCharacteristicProfile(
                characteristicUuid = writeUuid,
                required = true,
                enableNotification = false
            ),
            BleCharacteristicProfile(
                characteristicUuid = notifyUuid,
                required = true,
                enableNotification = true
            )
        ),
        serviceInterpreter = MyServiceInterpreter() // 自定义协议，见第 5 节
    )
)
```

### 2.3 手动连接

```kotlin
lifecycleScope.launch {
    val deviceResult = connectionManager.getBluetoothDeviceByMacNoScan("AA:BB:CC:DD:EE:FF")
    val device = deviceResult.getOrElse { return@launch }

    val connection = connectionManager.getOrCreateConnection(
        device = device,
        connectMode = ConnectMode.MANUAL,
        serviceProfiles = profiles,
        connectionTag = "racket" // 可选分组标签
    )

    val result = connection.connectAsync(timeoutMs = 35000)
    if (result.isSuccess) {
        // 已连接，可发指令
    }
}
```

### 2.4 断开连接

```kotlin
lifecycleScope.launch {
    connectionManager.disconnectDevice(address = "AA:BB:CC:DD:EE:FF")
    // 或
    connectionManager.disconnectByTag("racket")
    // 或
    connectionManager.disconnectAll()
}
```

---

## 3. 自动连接（推荐多设备场景）

采用 **扫连结合（Scan to Connect）**：设备不在身边时后台静默扫描，出现广播后再连接；支持排队串行连接与前台让路。

### 3.1 添加自动连接设备

```kotlin
autoConnectManager.addAutoConnectDevice(
    address = "AA:BB:CC:DD:EE:FF",
    name = "设备A",
    serviceProfiles = profiles,
    priority = 5,                          // 越大越优先
    connectMode = ConnectMode.AUTO_RECONNECT,
    maxRetryCount = 5,
    autoConnectDelay = 1000L,
    isPermanentlyEnabled = true            // 失败后是否保留在列表
)
```

### 3.2 监听自动连接状态

```kotlin
lifecycleScope.launch {
    autoConnectManager.autoConnectStates.collect { map ->
        map.forEach { (address, state) ->
            // Disabled / Waiting / Scanning / Connecting / Connected / Failed / Retrying
        }
    }
}
```

### 3.3 前台让路（重要）

进入手动搜索 / 连接页时，必须让后台扫连让出天线：

```kotlin
// 进入手动页
autoConnectManager.pauseAllForManualOperation()

// 退出手动页或手动连接结束
autoConnectManager.resumeAllFromManualOperation()
```

### 3.4 常用控制

```kotlin
autoConnectManager.setAutoConnectEnabled(true)
autoConnectManager.connectAllNow()

// 批量管理
autoConnectManager.addAutoConnectDevices(listOf(device1, device2))
autoConnectManager.removeAutoConnectDevices(listOf(addr1, addr2))
autoConnectManager.clearAllAutoConnectDevices()

// 单设备控制
autoConnectManager.manuallyDisconnectDevice(address) // 断开并暂停该设备自动重连
autoConnectManager.resumeAutoConnect(address)        // 恢复该设备自动连接
lifecycleScope.launch {
    autoConnectManager.manuallyConnectDevice(address)  // 绕过扫连，直接连接
}

// 取消与查询
autoConnectManager.cancelAutoConnection(address)
autoConnectManager.cancelAllAutoConnections()
autoConnectManager.isAutoConnectDevice(address)
autoConnectManager.getAutoConnectDevices()
autoConnectManager.getDeviceAutoConnectState(address)
autoConnectManager.getStatistics()
autoConnectManager.removeAutoConnectDevice(address)
```

---

## 3.5 单设备 GATT 进阶（BleConnection）

连接建立后，除通过 `BleConnectionManager` 发指令外，也可直接操作 `BleConnection`：

```kotlin
val connection = connectionManager.getConnection(address) ?: return

// 连接优先级（高吞吐场景）
connection.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

// 连接后动态开启更多特征监听
lifecycleScope.launch {
    connection.enableMultipleCharacteristics(
        listOf(
            BleCharacteristicProfile(notifyUuid, enableNotification = true)
        )
    )
}

// 单独开启 Notify / Indication
lifecycleScope.launch {
    val char = /* 从 discovered 特征或 gatt 获取 */
    connection.setCharacteristicNotification(char, enable = true)
    connection.setCharacteristicIndication(char, enable = true)
}
```

> 连接时若在 `BleCharacteristicProfile` 中已配置 `enableNotification` / `enableIndication`，库会在 `connectAsync` 成功后自动逐步开启，一般无需再手动调用。

---

## 4. 指令收发

### 4.1 同步发指令并等待响应

同一设备指令会串行排队；响应通过 `ServiceInterpreter.generateMessageId` 匹配。

```kotlin
lifecycleScope.launch {
    val result = connectionManager.sendCommandSync(
        deviceAddress = address,
        characteristicUuid = writeUuid,
        data = myCommandObject,   // 由 Interpreter.generate 转成字节
        timeoutMillis = 5000L
    )

    result.onSuccess { message ->
        // message.data / message.rawBytes / message.messageId
    }.onFailure { e ->
        if (e is BluetoothException) {
            // e.code / e.message
        }
    }
}
```

### 4.2 无响应写入（单设备）

向**单个**设备写入，不等待应答（`WRITE_TYPE_NO_RESPONSE`）：

```kotlin
connectionManager.sendCommandSyncNoResponse(
    deviceAddress = address,
    characteristicUuid = writeUuid,
    data = byteArrayOf(0x01, 0x02)
)
```

### 4.3 连接池与分组管理

```kotlin
// 按分组标签创建连接（getOrCreateConnection 的 connectionTag 参数）
val racketConnections = connectionManager.getConnectionsByTag("racket")
val allConnections = connectionManager.getAllConnections()

lifecycleScope.launch {
    connectionManager.cleanupIdleConnections(idleTimeoutMillis = 300_000L)
}

connectionManager.requestConnectionPriority(address, BluetoothGatt.CONNECTION_PRIORITY_HIGH)
```

### 4.4 多设备批量发送

```kotlin
lifecycleScope.launch {
    val results = connectionManager.sendCommandsToMultiple(
        deviceAddresses = listOf(addr1, addr2),
        characteristicUuid = writeUuid,
        data = command,
        timeoutMillis = 5000L,
        parallel = true
    )
}
```

### 4.5 监听设备主动推送

无匹配 `messageId` 的 Notify 数据会进入主动推送流：

```kotlin
lifecycleScope.launch {
    connectionManager.activePushes.collect { message ->
        // DeviceMessage: deviceAddress, characteristicUuid, data, rawBytes, timestamp
    }
}
```

### 4.6 监听连接状态

```kotlin
lifecycleScope.launch {
    connectionManager.connectionStates.collect { states ->
        // Map<MAC, ConnectionState>
    }
}
```

`ConnectionState`：`IDEL` / `Disconnected` / `Connecting` / `Connected` / `Reconnecting` / `Error`

---

## 5. 自定义协议（ServiceInterpreter）

每个 `BleServiceProfile` 绑定一个解释器，负责：

| 方法 | 作用 |
|------|------|
| `generate` | 业务对象 → 写入字节 |
| `interpret` | 收到字节 → 业务对象（简单同步场景） |
| `generateMessageId` | 生成请求/响应匹配 ID；返回空串则走主动推送 |
| `setOnDataParsedListener` | 异步解析（粘包等）完成后回调 |
| `release` | 断开时释放缓冲区 / 线程 |

### 5.1 示例

```kotlin
class MyServiceInterpreter : ServiceInterpreter {
    private var onParsed: ((UUID, Any) -> Unit)? = null

    override fun generate(characteristicUuid: UUID, data: Any): ByteArray {
        return when (data) {
            is ByteArray -> data
            is MyCommand -> data.toBytes()
            else -> error("unsupported")
        }
    }

    override fun interpret(
        byteArray: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        serviceProfile: BleServiceProfile
    ): Any? {
        // 同步解析可直接 return；复杂粘包建议走 listener
        val parsed = MyResponse.parse(byteArray)
        onParsed?.invoke(characteristic.uuid, parsed)
        return parsed
    }

    override fun generateMessageId(data: Any): String {
        return when (data) {
            is MyCommand -> data.seqId
            is MyResponse -> data.seqId
            is ByteArray -> "" // 空串 → 主动推送
            else -> ""
        }
    }

    override fun setOnDataParsedListener(listener: ((UUID, Any) -> Unit)?) {
        onParsed = listener
    }

    override fun release() {
        onParsed = null
    }
}
```

> **请求-响应配对**：发送与回复的 `generateMessageId` 必须一致，且非空，才会进入 `sendCommandSync` 的等待通道。

---

## 6. 扫描

### 6.1 按 MAC 批量扫描

```kotlin
lifecycleScope.launch {
    val results = connectionManager.scanDevices(
        macAddresses = listOf("AA:BB:CC:DD:EE:FF"),
        timeoutMillis = 10000L
    )
    results.forEach { (mac, result) ->
        result.getOrNull()?.device // ScanResult
    }
}
```

### 6.2 不扫描直接取设备

```kotlin
val device = connectionManager.getBluetoothDeviceByMacNoScan(mac).getOrNull()
```

---

## 7. 错误码

异常类型：`cn.xiaoyao.bluetooth.exception.BluetoothException(code, message)`

| 常量 | 值 | 含义 |
|------|----|------|
| `DEVICE_NOT_CONNECT` | 1001 | 设备未连接 |
| `OTHER_ERROR` | 1002 | 其他错误 |
| `RESPONSE_TIMEOUT` | 1003 | 响应超时 |
| `SERVICE_CONFIG_NOT_FOUND` | 1004 | 未找到服务配置 |
| `BLUETOOTH_PERMISSION_REQUIRED` | 1005 | 需要蓝牙权限 |
| `SEND_COMMAND_FAILED` | 1006 | 发送指令失败 |
| `COMMAND_CONFLICT` | 1007 | 命令冲突 |
| `COMMAND_CANCELLED` | 1008 | 命令取消 |
| `CHANNEL_CLOSED` | 1009 | 通道关闭 |
| `SYSTEM_CLEANUP` | 1010 | 系统清理 |

---

## 8. 推荐集成流程

```text
App 启动
  └─ BleConnectionManager.getInstance / BleAutoConnectManager.create
  └─ 加载已绑定设备 → addAutoConnectDevice(...)

进入手动配网页
  └─ pauseAllForManualOperation()
  └─ 扫描 / 手动 connectAsync
  └─ resumeAllFromManualOperation()

业务通信
  └─ sendCommandSync / activePushes.collect

退出 / 释放
  └─ disconnectAll() / connectionManager.release()
```

---

## 9. 注意事项

1. **先申请权限再扫连**，否则扫描/连接会失败。
2. **手动页务必调用前台让路**，避免与后台扫描抢占天线导致 133 等错误。
3. **连接需传入完整 `serviceProfiles`**，必需服务/特征缺失会连接失败。
4. **请求-响应依赖 messageId**，自定义协议时务必正确实现。
5. 单设备 GATT 超时默认约 35s；自动连接内部会排队并在设备间留缓冲间隔。
6. 断开后底层 GATT 会延迟释放，短时间重复连接时库内部已做等待处理。

---

## 10. 核心 API 速查

### BleConnectionManager

| API | 说明 |
|-----|------|
| `getInstance(context)` | 单例 |
| `getOrCreateConnection(...)` | 创建/获取连接（支持 `connectionTag` 分组） |
| `getConnection` / `getAllConnections` / `getConnectionsByTag` | 查询连接 |
| `connect` 经 `BleConnection.connectAsync` | 建立 GATT |
| `disconnectDevice` / `disconnectAll` / `disconnectByTag` | 断开 |
| `scanDevices` / `getBluetoothDeviceByMacNoScan` | 扫描/取设备 |
| `sendCommandSync` / `sendCommandSyncNoResponse` | 发指令（后者仅单设备） |
| `sendCommandsToMultiple` | 多设备发送 |
| `activePushes` / `connectionStates` | 推送与状态流 |
| `isDeviceConnected` / `getConnectedDevices` | 连接查询 |
| `cleanupIdleConnections` | 清理空闲连接 |
| `requestConnectionPriority` | 连接优先级 |
| `release` | 释放资源 |

### BleConnection

| API | 说明 |
|-----|------|
| `connectAsync` / `disconnect` | 连接与断开 |
| `isConnected` / `connectionState` | 状态 |
| `setCharacteristicNotification` / `setCharacteristicIndication` | 动态开关 Notify/Indicate |
| `enableMultipleCharacteristics` | 批量开启特征监听 |
| `writeCharacteristic` | 底层写入 |
| `requestConnectionPriority` | 连接优先级 |
| `release` | 释放资源 |

### BleAutoConnectManager

| API | 说明 |
|-----|------|
| `create(context)` | 创建（内部复用 ConnectionManager） |
| `addAutoConnectDevice` / `addAutoConnectDevices` | 添加设备 |
| `removeAutoConnectDevice` / `removeAutoConnectDevices` / `clearAllAutoConnectDevices` | 移除设备 |
| `setAutoConnectEnabled` / `isAutoConnectEnabled` | 全局开关 |
| `pauseAllForManualOperation` / `resumeAllFromManualOperation` | 前台让路 |
| `manuallyConnectDevice` / `manuallyDisconnectDevice` | 单设备手动控制 |
| `resumeAutoConnect` / `connectAllNow` | 恢复/立即连接 |
| `cancelAutoConnection` / `cancelAllAutoConnections` | 取消连接任务 |
| `isAutoConnectDevice` / `getAutoConnectDevices` / `getDeviceAutoConnectState` | 状态查询 |
| `autoConnectStates` / `isAutoConnectEnabledFlow` / `getStatistics` | 状态流与统计 |

---

## 11. 常见问题与排错

### 11.1 连接时报 133 / 连接不稳定

常见原因：

1. 前台手动连接和后台自动扫描同时抢占蓝牙资源。
2. 设备刚断开就立刻重复连接，底层 GATT 还未完全释放。
3. 多台设备短时间连续 `connectGatt`，导致系统蓝牙栈拥堵。

排查建议：

- 进入手动连接页面前先调用：

```kotlin
autoConnectManager.pauseAllForManualOperation()
```

- 手动流程结束后再恢复：

```kotlin
autoConnectManager.resumeAllFromManualOperation()
```

- 使用库内自动连接时，尽量通过 `BleAutoConnectManager` 统一排队，不要自己并发发起多个连接。
- 断开后避免立即重连；库内已做延迟释放，但业务层仍不要高频反复点击连接。

### 11.2 `sendCommandSync` 一直超时

优先检查以下几点：

1. 设备是否真的已连接：`connectionManager.isDeviceConnected(address)`
2. `characteristicUuid` 是否对应可写特征
3. `BleServiceProfile` 是否配置正确，且目标特征属于已声明服务
4. 协议层 `generateMessageId` 是否能让“请求”和“响应”匹配上

如果命中超时异常，一般会得到：

- `RESPONSE_TIMEOUT`
- `DEVICE_NOT_CONNECT`
- `SERVICE_CONFIG_NOT_FOUND`

建议先打印：

- 发送数据内容
- 请求 `messageId`
- 响应 `messageId`
- 实际回调到的特征 UUID

### 11.3 收到设备通知了，但 `sendCommandSync` 仍匹配不到响应

这是最常见的协议问题。当前库的匹配规则是：

- 发送时，使用 `serviceInterpreter.generateMessageId(data)` 生成请求 ID
- 接收时，解析后再次生成响应 ID
- 两者一致时，才会进入同步等待通道

如果返回空串或不一致：

- 该消息不会作为同步响应命中
- 很可能会落入 `activePushes`，表现为“设备有返回，但同步调用超时”

建议：

```kotlin
override fun generateMessageId(data: Any): String {
    return when (data) {
        is MyCommand -> data.seqId
        is MyResponse -> data.seqId
        else -> ""
    }
}
```

### 11.4 自动连接一直处于 `Scanning`，不进入 `Connected`

可能原因：

1. 目标设备当前没有广播
2. MAC 地址错误
3. 蓝牙未开启
4. 扫描权限未授予
5. 服务配置不匹配，连接后初始化失败又回到扫描态

建议按顺序排查：

1. 用 `scanDevices(...)` 单独验证能否扫到该 MAC
2. 用 `getBluetoothDeviceByMacNoScan(...)` 验证 MAC 格式是否合法
3. 确认 `addAutoConnectDevice(...)` 传入的 `serviceProfiles` 正确
4. 观察 `autoConnectStates.collect` 中状态是否经历了 `Connecting -> Failed/Scanning`

### 11.5 提示缺少权限

常见错误码：

- `BLUETOOTH_PERMISSION_REQUIRED`

处理建议：

1. 先检查系统蓝牙是否打开
2. Android 12+ 确认已授予：
   - `BLUETOOTH_SCAN`
   - `BLUETOOTH_CONNECT`
3. 某些 ROM 扫描还需要定位权限或系统定位开关

如果扫描不到设备，但代码本身无异常，优先检查权限与系统设置，而不是先怀疑协议层。

### 11.6 提示 `SERVICE_CONFIG_NOT_FOUND`

这通常说明你在发送指令时，当前 `characteristicUuid` 无法在该连接对应的 `serviceProfiles` 中找到。

重点检查：

- `getOrCreateConnection(...)` 时是否传入了正确的 `serviceProfiles`
- `BleServiceProfile.serviceUuid` 是否与真实设备一致
- `BleCharacteristicProfile.characteristicUuid` 是否写错
- 该特征是否属于当前连接的那台设备

### 11.7 Notify / Indicate 没有生效

建议检查：

1. 特征属性是否真的支持 `PROPERTY_NOTIFY` 或 `PROPERTY_INDICATE`
2. 连接时是否已通过 `BleCharacteristicProfile` 配置：
   - `enableNotification = true`
   - `enableIndication = true`
3. 若是连接后动态开启，确认调用的是：

```kotlin
connection.setCharacteristicNotification(characteristic, true)
connection.setCharacteristicIndication(characteristic, true)
```

4. 设备端是否真的在对应特征上发送通知

### 11.8 如何判断该用手动连接还是自动连接

- 单设备、临时配网、调试场景：优先手动连接
- 多设备常驻、靠近即连、掉线自动恢复：优先 `BleAutoConnectManager`

推荐做法：

- 页面内手动操作时暂停自动连接
- 页面退出后恢复自动连接
- 不要让同一台设备同时处于“手动连接流程”和“后台自动连接流程”

---

## 12. 日志建议（建议你至少打印这些字段）

当你遇到连接/超时/messageId 配对问题时，建议按下面“最少集合”去补充日志（不需要全量打印，关键是定位到是哪个阶段出了问题）。

### 12.1 发送指令相关（`sendCommandSync`）

在调用 `sendCommandSync` 前后，建议打印：

- 设备 `deviceAddress`
- `characteristicUuid`
- 本次发送的 `data`（至少打印序列化后的关键字段）
- `messageId = serviceInterpreter.generateMessageId(data)`
- `timeoutMillis`
- 如果失败：错误码（例如 `RESPONSE_TIMEOUT` / `SERVICE_CONFIG_NOT_FOUND` / `BLUETOOTH_PERMISSION_REQUIRED`）

### 12.2 接收与匹配相关（Notify/Indicate 回调）

当收到通知（`onCharacteristicChanged`）并完成解释后，建议打印：

- `deviceAddress`
- 接收到的 `characteristic.uuid`
- 解析后的 `parsedData`
- `messageId = interpreter.generateMessageId(parsedData)`（如果你的协议层能拿到生成逻辑）
- 最终是进入了：
  - 同步响应通道（`pendingResponses` 命中）
  - 还是进入 `activePushes`（作为主动推送）

> 经验：如果 `sendCommandSync` 超时，但设备确实在返回数据，99% 是 `messageId` 不一致或请求/响应对象没有对应到同一套 ID 规则。

### 12.3 连接阶段相关（`BleConnection`）

连接成功前后的关键日志建议看：

- `Connecting / Connected / Disconnected / Error`（`connectionStates`）
- 服务发现：`onServicesDiscovered -> processDiscoveredServices`（必需服务/特征是否满足）
- MTU 协商结果（`onMtuChanged`）
- CCCD 描述符写入结果（`onDescriptorWrite`）

### 12.4 自动连接阶段相关（`BleAutoConnectManager`）

自动连接一直停留在 `Scanning/Connecting` 时，建议确认：

- 是否正确调用了 `pauseAllForManualOperation()` / `resumeAllFromManualOperation()`
- 扫描队列是否还有待处理设备（`autoConnectStates` 中该地址的状态）
- 该设备在系统蓝牙层是否真的有广播（可用系统 BLE 扫描工具交叉验证）

---

如果你愿意，我可以把“日志最少集合”进一步落到你的项目：告诉我你当前设备协议是怎么构造 `data`/`messageId` 的（比如序列号字段 `seqId` 怎么生成），我可以给你一份可直接粘贴的打印/校验模板。
