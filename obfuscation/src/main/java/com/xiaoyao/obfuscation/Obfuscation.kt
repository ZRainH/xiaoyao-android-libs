package com.xiaoyao.obfuscation

/**
 * 规则包标记类：确认依赖已打进宿主包即可。
 * 实际混淆规则通过 AAR 的 consumer ProGuard 文件自动合并。
 */
object Obfuscation {
    const val VERSION: String = "1.0.0"
}
