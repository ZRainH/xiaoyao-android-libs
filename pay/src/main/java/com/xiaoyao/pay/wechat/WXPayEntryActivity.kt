package com.xiaoyao.pay.wechat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.xiaoyao.pay.factory.wechat.WxPayCallbackHolder
import com.xiaoyao.pay.model.PayResultCode
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory

/**
 * 微信支付结果回调基类。
 *
 * 宿主需创建 `{applicationId}.wxapi.WXPayEntryActivity` 并继承本类，例如：
 * ```
 * package com.xxx.xxx.wxapi
 * class WXPayEntryActivity : com.xiaoyao.pay.wechat.WXPayEntryActivity()
 * ```
 */
open class WXPayEntryActivity : Activity(), IWXAPIEventHandler {

    private var api: IWXAPI? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = WXAPIFactory.createWXAPI(this, null).also {
            it.handleIntent(intent, this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        api?.handleIntent(intent, this)
    }

    override fun onReq(req: BaseReq?) = Unit

    override fun onResp(resp: BaseResp?) {
        val listener = WxPayCallbackHolder.take()
        if (resp?.type == ConstantsAPI.COMMAND_PAY_BY_WX) {
            when (resp.errCode) {
                BaseResp.ErrCode.ERR_OK -> {
                    listener?.onSuccess()
                    listener?.onComplete()
                }
                BaseResp.ErrCode.ERR_USER_CANCEL -> {
                    listener?.onFailure(PayResultCode.CANCEL, "支付取消")
                    listener?.onComplete()
                }
                else -> {
                    listener?.onFailure(
                        resp.errCode.toString(),
                        resp.errStr ?: "支付失败"
                    )
                    listener?.onComplete()
                }
            }
        }
        finish()
    }
}
