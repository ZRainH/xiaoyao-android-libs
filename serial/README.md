# Serial 串口库

Android 原生串口会话库，基于 [android-serialport](https://github.com/licheedev/Android-SerialPort-API)。

| 项 | 值 |
|----|-----|
| 版本 | `1.2.0` |
| Maven | `io.github.zrainh:serial` |
| 包名 | `com.xiaoyao.serial` |

能力：多路连接、协议拆帧、发请求等应答（`execute` / `enqueue`）。

---

## 依赖

```kotlin
// 同工程
implementation(project(":serial"))

// Maven Central
implementation("io.github.zrainh:serial:1.2.0")
```

---

## 快速开始

```kotlin
val manager = SerialPortManager.getInstance()

val conn = manager.openPort(
    connectionId = "main",
    path = "/dev/ttyS1",
    baudRate = 9600
).getOrThrow()

// 收：open 后尽快 collect
lifecycleScope.launch {
    conn.incomingFrames.collect { frame -> /* 完整帧 */ }
}

// 发
lifecycleScope.launch {
    conn.writeText("AT\r\n").getOrThrow()
}

manager.close("main")
```

---

## 怎么用

| 场景 | API |
|------|-----|
| 原始收发 | `write` + `incomingBytes` |
| 拆帧后收发 | `send` + `incomingFrames`（需配置 ProtocolAdapter） |
| 发请求等应答 | `execute`（挂起）或 `enqueue`（回调） |

结构：`SerialPortManager` → `SerialConnection` → `SerialTransport` + `SerialProtocolAdapter`。

---

## 打开 / 关闭

```kotlin
manager.listDevicePaths()   // 枚举 /dev/tty*

manager.openPort(
    connectionId = "main",
    path = "/dev/ttyS1",
    baudRate = 9600,
    config = SerialConfig(readTimeoutMs = 200, executeTimeoutMs = 1000),
    protocol = RawProtocolAdapter   // 默认透传
)

manager.close("main")
manager.closeAll()
```

- 同一物理口不能开两个 `connectionId`
- `/dev/tty*` 通常要系统权限或 root
- `ERROR` 后需重新 `open`

---

## 请求-应答

### execute（协程挂起）

```kotlin
val result = conn.execute(
    request = frame,
    timeoutMs = 1000,
    encode = false
) { rsp ->
    // 判断是不是本笔应答（建议带 messageId / 功能码）
    rsp[4] == frame[4] && rsp[5] == frame[5]
}
```

### enqueue（异步回调）

```kotlin
val job = conn.enqueue(
    request = frame,
    timeoutMs = 1000,
    matcher = { rsp -> /* 同上 */ },
    onComplete = { result ->
        result.onSuccess { /* 应答 */ }
    }
)
job.cancel()   // 可选取消
```

要点：

- **matcher 要准**：能唯一识别本笔应答；过宽会配错
- 有流水号：`serializeExecute = false`，可并发
- 无流水号：`serializeExecute = true`，串行发
- 匹配到的帧不进 `incomingFrames`；超时后迟到的帧可能进推送

---

## 协议适配器

```kotlin
RawProtocolAdapter                    // 默认，read chunk 即一帧

DelimiterProtocolAdapter.crlf()       // 按 \r\n 切行
DelimiterProtocolAdapter.line()       // 按 \n

LengthFieldProtocolAdapter(            // [长度][载荷]
    lengthOffset = 0,
    lengthSize = 1
)
```

自定义：实现 `SerialProtocolAdapter`（`encode` / `feed` / `reset`），每路连接用独立实例。

---

## 常用配置

| 字段 | 默认 | 说明 |
|------|------|------|
| `readTimeoutMs` | `200` | 读等待；`<=0` 阻塞到有数据 |
| `writeTimeoutMs` | `2000` | 写超时 |
| `executeTimeoutMs` | `1000` | execute / enqueue 超时 |
| `serializeExecute` | `false` | 是否串行发请求 |

---

## 错误码

`SerialException.code`：`OPEN_FAILED`、`NOT_CONNECTED`、`WRITE_FAILED`、`READ_FAILED`、`TIMEOUT` 等。

读写和 execute 返回 `Result`，一般不抛异常。

---

## 注意

1. `open` 后立刻 collect，否则缓冲满会丢数据
2. UI 更新：`incomingFrames` / `enqueue` 回调在 IO 线程，切 Main 再改 UI
3. 订阅 `conn.state`，`ERROR` 时重连
4. Modbus 用 `:modbus` 模块（Raw + incomingBytes），不必用 execute

---

## 库结构（简）

```
com.xiaoyao.serial
├── SerialPortManager       # 多路管理
├── connection.SerialConnection
├── transport.*             # NativeSerialTransport
├── protocol.*              # Raw / Delimiter / LengthField
├── model.*                 # SerialConfig 等
└── exception.*
```

测试：`./gradlew :serial:testDebugUnitTest`
