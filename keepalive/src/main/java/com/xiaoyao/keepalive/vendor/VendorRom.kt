package com.xiaoyao.keepalive.vendor

import android.os.Build

enum class VendorBrand {
    XIAOMI,
    HUAWEI,
    HONOR,
    OPPO,
    VIVO,
    MEIZU,
    SAMSUNG,
    ONEPLUS,
    OTHER
}

/**
 * 识别常见国产 ROM 厂商。
 */
object VendorRom {
    fun brand(): VendorBrand {
        val m = Build.MANUFACTURER.orEmpty().lowercase()
        val b = Build.BRAND.orEmpty().lowercase()
        val text = "$m $b"
        return when {
            text.contains("xiaomi") || text.contains("redmi") || text.contains("poco") ->
                VendorBrand.XIAOMI
            text.contains("huawei") -> VendorBrand.HUAWEI
            text.contains("honor") -> VendorBrand.HONOR
            text.contains("oppo") || text.contains("realme") -> VendorBrand.OPPO
            text.contains("vivo") || text.contains("iqoo") -> VendorBrand.VIVO
            text.contains("meizu") -> VendorBrand.MEIZU
            text.contains("samsung") -> VendorBrand.SAMSUNG
            text.contains("oneplus") -> VendorBrand.ONEPLUS
            else -> VendorBrand.OTHER
        }
    }

    fun displayName(): String = when (brand()) {
        VendorBrand.XIAOMI -> "小米 / Redmi"
        VendorBrand.HUAWEI -> "华为"
        VendorBrand.HONOR -> "荣耀"
        VendorBrand.OPPO -> "OPPO / realme"
        VendorBrand.VIVO -> "vivo / iQOO"
        VendorBrand.MEIZU -> "魅族"
        VendorBrand.SAMSUNG -> "三星"
        VendorBrand.ONEPLUS -> "一加"
        VendorBrand.OTHER -> Build.MANUFACTURER.orEmpty().ifBlank { "未知厂商" }
    }
}
