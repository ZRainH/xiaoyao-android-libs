package com.xiaoyao.pay.model

/**
 * 支付宝 SDK 返回结果封装。
 */
data class AliPayResult(
    val resultStatus: String? = null,
    val result: String? = null,
    val memo: String? = null
) {
    companion object {
        const val STATUS_SUCCESS = "9000"

        fun from(raw: Map<String, String>?): AliPayResult {
            if (raw == null) return AliPayResult()
            return AliPayResult(
                resultStatus = raw["resultStatus"],
                result = raw["result"],
                memo = raw["memo"]
            )
        }
    }

    val isSuccess: Boolean
        get() = resultStatus == STATUS_SUCCESS
}
