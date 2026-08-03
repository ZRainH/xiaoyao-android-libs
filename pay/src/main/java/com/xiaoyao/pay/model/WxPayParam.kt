package com.xiaoyao.pay.model

/**
 * 微信 APP 支付预下单参数（由服务端统一下单后返回）。
 */
data class WxPayParam(
    val appId: String,
    val partnerId: String,
    val prepayId: String,
    val packageValue: String = "Sign=WXPay",
    val nonceStr: String,
    val timeStamp: String,
    val sign: String
)
