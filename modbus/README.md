# Modbus RTU 主站库

基于 `:serial` 的 Modbus RTU 主机实现，支持常用功能码与 CRC 校验。

- **版本**：`1.0.0`
- **坐标**：`io.github.zrainh:modbus`（发布后）
- **包名**：`com.xiaoyao.modbus`

---

## 依赖

同工程：

```kotlin
implementation(project(":modbus"))
```

会传递依赖 `:serial`。

---

## 支持的功能码

| 码 | API |
|----|-----|
| 01 | `readCoils` |
| 02 | `readDiscreteInputs` |
| 03 | `readHoldingRegisters` |
| 04 | `readInputRegisters` |
| 05 | `writeSingleCoil` |
| 06 | `writeSingleRegister` |
| 15 | `writeMultipleCoils` |
| 16 | `writeMultipleRegisters` |

---

## 使用说明

```kotlin
val connection = SerialPortManager.getInstance().openPort(
    connectionId = "modbus",
    path = "/dev/ttyS1",
    baudRate = 9600
).getOrThrow()

val master = ModbusMaster(
    connection = connection,
    slaveId = 1,
    baudRate = 9600,
    timeoutMs = 1000
)

lifecycleScope.launch {
    // 读保持寄存器
    master.readHoldingRegisters(address = 0, quantity = 10)
        .onSuccess { regs -> /* IntArray */ }
        .onFailure { e -> /* ModbusException */ }

    // 写单个线圈
    master.writeSingleCoil(address = 0, on = true)

    // 写多个寄存器
    master.writeMultipleRegisters(0, intArrayOf(1, 1, 1, 1, 0, 0, 0, 0, 1, 1))
}
```

### 仅协议层（自定义传输）

```kotlin
val transport: ModbusTransport = SerialModbusTransport(connection, baudRate = 9600)
val master = ModbusMaster(transport, slaveId = 1)
```

---

## 请求 / 响应一一对应（已加强）

RTU **没有** TCP 的 TransactionId。本库用「字段匹配 + 过程约束 + 写后读回」降低误匹配：

| 手段 | 作用 |
|------|------|
| 事务 `Mutex` 串行 | 同一连接同时只有一问一答 |
| 发前 drain + 静默 | 清掉迟到的旧应答 |
| `ModbusRequestContext` | 期望从站/功能码/地址/数量/写回显 |
| 软匹配 + 严格字段校验 | 粘包中挑对本请求的帧 |
| 收后静默再 drain | 丢掉应答后粘上的多余字节 |
| 失败自动重试 | 超时/CRC/错帧可配置重试 |
| **写后读回校验** | 写成功后再读比对（默认开启） |

```kotlin
val master = ModbusMaster(
    connection = connection,
    slaveId = 1,
    baudRate = 9600,
    config = ModbusMasterConfig(
        maxRetries = 2,
        verifyWrites = true,      // 写后读回
        preSendSettleMs = 20,
        postReceiveSettleMs = 20
    )
)
```

关闭写后校验可用 `ModbusMasterConfig.FAST`。

`SerialModbusTransport` 采用完整拆帧策略：

1. **发请求前 drain**：清空接收缓冲，避免旧数据污染  
2. **后台持续收字节**：半包会先入缓冲再拼齐  
3. **按功能码定长切帧**：01/02/03/04 用 byteCount；05/06/0F/10 固定 8 字节；异常帧 5 字节  
4. **CRC 校验**：长度够后验 CRC，失败则滑动 1 字节重新同步  
5. **多帧粘连**：校验通过但非本次应答（地址/功能码不符）则整帧跳过，继续取下一帧  
6. **3.5 字符空闲**：仅用于等待更多字节，不单独作为唯一定界条件  

也可直接使用 `ModbusRtuFrameExtractor` 做离线拆帧。
