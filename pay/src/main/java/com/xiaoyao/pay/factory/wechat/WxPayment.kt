package com.xiaoyao.pay.factory.wechat

import android.content.Context
import com.xiaoyao.pay.callback.OnPayListener
import com.xiaoyao.pay.model.WxPayParam

/**
 * 微信支付产品抽象。
 */
interface WxPayment {
    fun startWxPayment(context: Context, param: WxPayParam, listener: OnPayListener)
}
