package com.xiaoyao.pay.callback

/**
 * 支付结果回调。
 * [onComplete] 默认空实现，成功/失败后都会触发。
 */
interface OnPayListener {
    fun onSuccess()
    fun onFailure(errorCode: String, errorMsg: String)
    fun onComplete() {}
}
