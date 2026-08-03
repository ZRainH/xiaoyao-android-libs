package com.xiaoyao.pay.factory.alipay

import android.app.Activity
import com.alipay.sdk.app.PayTask
import com.xiaoyao.pay.callback.OnPayListener
import com.xiaoyao.pay.model.AliPayResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 支付宝支付产品实现（协程替代 RxJava）。
 */
class AliPaymentProduct(
    private val scope: CoroutineScope
) : AliPayment {

    override fun startAliPayment(
        activity: Activity,
        orderInfo: String,
        listener: OnPayListener
    ) {
        scope.launch {
            val payResult = withContext(Dispatchers.IO) {
                AliPayResult.from(PayTask(activity).payV2(orderInfo, true))
            }
            dispatch(payResult, listener)
        }
    }

    private fun dispatch(result: AliPayResult, listener: OnPayListener) {
        if (result.isSuccess) {
            listener.onSuccess()
        } else {
            listener.onFailure(
                result.resultStatus.orEmpty(),
                result.memo.orEmpty()
            )
        }
        listener.onComplete()
    }
}
