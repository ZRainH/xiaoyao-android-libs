package com.xiaoyao.pay

import android.app.Activity
import android.content.Context
import com.xiaoyao.pay.callback.OnPayListener
import com.xiaoyao.pay.factory.PaymentFactory
import com.xiaoyao.pay.factory.PaymentProduct
import com.xiaoyao.pay.factory.alipay.AliPayment
import com.xiaoyao.pay.factory.union.UnionPayment
import com.xiaoyao.pay.factory.wechat.WxPayment
import com.xiaoyao.pay.model.WxPayParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 支付入口。通过抽象工厂创建各渠道产品。
 */
object PaymentManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val factory: PaymentFactory by lazy { PaymentProduct(scope) }

    private val aliPay: AliPayment by lazy { factory.createAliPayment() }

    private val unionPay: UnionPayment by lazy { factory.createUnionPayment() }

    private val wxPay: WxPayment by lazy { factory.createWxPayment() }

    fun startAliPay(activity: Activity, orderInfo: String, listener: OnPayListener) {
        require(orderInfo.isNotBlank()) { "orderInfo must not be blank" }
        aliPay.startAliPayment(activity, orderInfo, listener)
    }

    fun startUnionPay(
        context: Context,
        tn: String,
        isDebug: Boolean,
        listener: OnPayListener
    ) {
        require(tn.isNotBlank()) { "tn must not be blank" }
        unionPay.startUnionPayment(context, tn, isDebug, listener)
    }

    fun startWxPay(context: Context, param: WxPayParam, listener: OnPayListener) {
        require(param.appId.isNotBlank()) { "appId must not be blank" }
        require(param.prepayId.isNotBlank()) { "prepayId must not be blank" }
        wxPay.startWxPayment(context, param, listener)
    }
}
