package com.xiaoyao.pay.factory.union

import android.content.Context
import com.xiaoyao.pay.callback.OnPayListener
import com.xiaoyao.pay.union.UnionPayActivity

/**
 * 银联支付产品实现。
 */
class UnionPaymentProduct : UnionPayment {

    override fun startUnionPayment(
        context: Context,
        tn: String,
        isDebug: Boolean,
        listener: OnPayListener
    ) {
        UnionPayActivity.start(context, tn, isDebug, listener)
    }
}
