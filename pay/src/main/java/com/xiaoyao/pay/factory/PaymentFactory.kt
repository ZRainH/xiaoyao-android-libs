package com.xiaoyao.pay.factory

import com.xiaoyao.pay.factory.alipay.AliPayment
import com.xiaoyao.pay.factory.union.UnionPayment
import com.xiaoyao.pay.factory.wechat.WxPayment

/**
 * 抽象工厂：创建各支付渠道产品。
 */
interface PaymentFactory {
    fun createAliPayment(): AliPayment
    fun createUnionPayment(): UnionPayment
    fun createWxPayment(): WxPayment
}
