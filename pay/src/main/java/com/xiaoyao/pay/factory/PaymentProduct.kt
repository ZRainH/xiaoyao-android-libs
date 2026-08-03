package com.xiaoyao.pay.factory

import com.xiaoyao.pay.factory.alipay.AliPayment
import com.xiaoyao.pay.factory.alipay.AliPaymentProduct
import com.xiaoyao.pay.factory.union.UnionPayment
import com.xiaoyao.pay.factory.union.UnionPaymentProduct
import com.xiaoyao.pay.factory.wechat.WxPayment
import com.xiaoyao.pay.factory.wechat.WxPaymentProduct
import kotlinx.coroutines.CoroutineScope

/**
 * 具体工厂：负责实例化各支付渠道实现。
 */
class PaymentProduct(
    private val scope: CoroutineScope
) : PaymentFactory {

    override fun createAliPayment(): AliPayment = AliPaymentProduct(scope)

    override fun createUnionPayment(): UnionPayment = UnionPaymentProduct()

    override fun createWxPayment(): WxPayment = WxPaymentProduct()
}
