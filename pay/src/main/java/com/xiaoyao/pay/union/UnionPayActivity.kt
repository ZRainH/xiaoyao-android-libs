package com.xiaoyao.pay.union

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.xiaoyao.pay.callback.OnPayListener
import com.xiaoyao.pay.model.PayResultCode
import com.unionpay.UPPayAssistEx

internal class UnionPayActivity : Activity() {

    private val tn: String by lazy { intent.getStringExtra(EXTRA_TN).orEmpty() }
    private val debug: Boolean by lazy { intent.getBooleanExtra(EXTRA_DEBUG, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = if (debug) MODE_TEST else MODE_PRODUCT
        UPPayAssistEx.startPay(this, null, null, tn, mode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val listener = CallbackHolder.take()
        when (data?.getStringExtra(KEY_PAY_RESULT)) {
            RESULT_SUCCESS -> {
                listener?.onSuccess()
                listener?.onComplete()
            }
            RESULT_FAIL -> {
                listener?.onFailure(PayResultCode.FAILURE, "支付失败")
                listener?.onComplete()
            }
            RESULT_CANCEL -> {
                listener?.onFailure(PayResultCode.CANCEL, "支付取消")
                listener?.onComplete()
            }
            else -> {
                listener?.onFailure(PayResultCode.FAILURE, "支付失败")
                listener?.onComplete()
            }
        }
        finish()
    }

    companion object {
        private const val EXTRA_TN = "tn"
        private const val EXTRA_DEBUG = "isDebug"
        private const val KEY_PAY_RESULT = "pay_result"
        private const val RESULT_SUCCESS = "success"
        private const val RESULT_FAIL = "fail"
        private const val RESULT_CANCEL = "cancel"
        private const val MODE_TEST = "01"
        private const val MODE_PRODUCT = "00"

        fun start(context: Context, tn: String, debug: Boolean, listener: OnPayListener) {
            CallbackHolder.set(listener)
            context.startActivity(
                Intent(context, UnionPayActivity::class.java).apply {
                    putExtra(EXTRA_TN, tn)
                    putExtra(EXTRA_DEBUG, debug)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private object CallbackHolder {
        @Volatile
        private var listener: OnPayListener? = null

        fun set(callback: OnPayListener) {
            listener = callback
        }

        fun take(): OnPayListener? = listener.also { listener = null }
    }
}
