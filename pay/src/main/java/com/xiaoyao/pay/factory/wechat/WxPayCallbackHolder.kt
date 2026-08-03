package com.xiaoyao.pay.factory.wechat

import com.xiaoyao.pay.callback.OnPayListener

/**
 * 微信支付回调暂存（由宿主 wxapi.WXPayEntryActivity 取走）。
 */
internal object WxPayCallbackHolder {

    @Volatile
    private var listener: OnPayListener? = null

    fun set(callback: OnPayListener) {
        listener = callback
    }

    fun take(): OnPayListener? = listener.also { listener = null }

    fun peek(): OnPayListener? = listener
}
