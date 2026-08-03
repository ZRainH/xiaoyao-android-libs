package com.xiaoyao.pay.factory.union

import android.content.Context
import com.xiaoyao.pay.callback.OnPayListener

/**
 * 银联支付产品抽象。
 */
interface UnionPayment {
    fun startUnionPayment(
        context: Context,
        tn: String,
        isDebug: Boolean,
        listener: OnPayListener
    )
}
