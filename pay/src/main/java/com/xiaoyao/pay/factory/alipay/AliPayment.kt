package com.xiaoyao.pay.factory.alipay

import android.app.Activity
import com.xiaoyao.pay.callback.OnPayListener

/**
 * 支付宝支付产品抽象。
 */
interface AliPayment {
    fun startAliPayment(activity: Activity, orderInfo: String, listener: OnPayListener)
}
