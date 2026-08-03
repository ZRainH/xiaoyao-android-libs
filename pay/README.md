# Pay 支付工具库

支持 **支付宝**、**微信**、**银联**，统一入口 `PaymentManager`。

- **版本**：`1.0.0`（另有 `1.0.1`，内容相同，推荐用 `1.0.0`）
- **坐标**：`io.github.zrainh:pay`
- **包名**：`com.xiaoyao.pay`

---

## 依赖

```kotlin
dependencies {
    implementation("io.github.zrainh:pay:1.0.0")
}
```

仓库需包含 `mavenCentral()`。

### 微信回调（必配）

微信要求回调类为 `{applicationId}.wxapi.WXPayEntryActivity`。同仓库接入时根工程会自动 apply `pay/wxentry.gradle`；外部工程请在 **app** 模块增加：

```kotlin
// app/build.gradle.kts
apply(from = "path/to/wxentry.gradle")
```

也可自行创建 `{applicationId}.wxapi.WXPayEntryActivity`，继承 `com.xiaoyao.pay.wechat.WXPayEntryActivity`。

### 权限与环境

库内已声明常用权限与微信包可见性（Android 11+）：

- `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` / `READ_PHONE_STATE`
- `<queries>`：`com.tencent.mm`

建议：`minSdk` ≥ 26（与当前工程一致）。

---

## 使用说明

### 统一回调

```kotlin
import com.xiaoyao.pay.callback.OnPayListener

val listener = object : OnPayListener {
    override fun onSuccess() {
        // 支付成功
    }

    override fun onFailure(errorCode: String, errorMsg: String) {
        // 失败 / 取消等
    }

    override fun onComplete() {
        // 成功、失败后都会走到（可选重写）
    }
}
```

银联等通用结果码：

| 常量 | 值 | 含义 |
|------|----|------|
| `PayResultCode.SUCCESS` | `01` | 成功 |
| `PayResultCode.FAILURE` | `02` | 失败 |
| `PayResultCode.CANCEL` | `03` | 取消 |

### 支付宝

服务端生成订单串 `orderInfo` 后调起（需在 `Activity` 中调用）：

```kotlin
import com.xiaoyao.pay.PaymentManager

PaymentManager.startAliPay(
    activity = this,
    orderInfo = orderInfo,
    listener = listener
)
```

### 微信支付

服务端统一下单后，将预支付参数组装为 `WxPayParam`：

```kotlin
import com.xiaoyao.pay.PaymentManager
import com.xiaoyao.pay.model.WxPayParam

PaymentManager.startWxPay(
    context = this,
    param = WxPayParam(
        appId = "wx........",
        partnerId = "商户号",
        prepayId = "服务端返回",
        packageValue = "Sign=WXPay",
        nonceStr = "服务端返回",
        timeStamp = "服务端返回",
        sign = "服务端返回"
    ),
    listener = listener
)
```

注意：需安装微信；应用包名、签名须与微信开放平台一致；务必配置 `wxentry.gradle` 或等价回调类。

### 银联支付

```kotlin
import com.xiaoyao.pay.PaymentManager

PaymentManager.startUnionPay(
    context = this,
    tn = tn,            // 服务端下发的交易流水号
    isDebug = false,    // true=测试环境，false=生产环境
    listener = listener
)
```

---

## API 一览

| 方法 | 说明 |
|------|------|
| `PaymentManager.startAliPay(activity, orderInfo, listener)` | 支付宝 |
| `PaymentManager.startWxPay(context, param, listener)` | 微信 |
| `PaymentManager.startUnionPay(context, tn, isDebug, listener)` | 银联 |

核心类型：

- `com.xiaoyao.pay.PaymentManager`
- `com.xiaoyao.pay.callback.OnPayListener`
- `com.xiaoyao.pay.model.WxPayParam`
- `com.xiaoyao.pay.model.PayResultCode`
