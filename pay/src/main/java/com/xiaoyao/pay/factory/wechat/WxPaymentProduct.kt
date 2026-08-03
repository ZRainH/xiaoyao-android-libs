package com.xiaoyao.pay.factory.wechat

import android.content.Context
import com.xiaoyao.pay.callback.OnPayListener
import com.xiaoyao.pay.model.PayResultCode
import com.xiaoyao.pay.model.WxPayParam
import com.tencent.mm.opensdk.modelpay.PayReq
import com.tencent.mm.opensdk.openapi.WXAPIFactory

/**
 * 微信支付产品实现。
 *
 * 宿主侧的 `{applicationId}.wxapi.WXPayEntryActivity` 由 `pay/wxentry.gradle`
 * 在编译期自动生成，同仓库 application 模块无需手动配置。
 */
class WxPaymentProduct : WxPayment {

    override fun startWxPayment(
        context: Context,
        param: WxPayParam,
        listener: OnPayListener
    ) {
        val api = WXAPIFactory.createWXAPI(context.applicationContext, param.appId, true)
        if (!api.isWXAppInstalled) {
            listener.onFailure(PayResultCode.FAILURE, "未安装微信")
            listener.onComplete()
            return
        }

        WxPayCallbackHolder.set(listener)
        api.registerApp(param.appId)

        val req = PayReq().apply {
            appId = param.appId
            partnerId = param.partnerId
            prepayId = param.prepayId
            packageValue = param.packageValue
            nonceStr = param.nonceStr
            timeStamp = param.timeStamp
            sign = param.sign
        }

        val sent = api.sendReq(req)
        if (!sent) {
            WxPayCallbackHolder.take()?.let {
                it.onFailure(PayResultCode.FAILURE, "调起微信支付失败")
                it.onComplete()
            }
        }
    }
}
