package com.tennis.base.mvi

/**
 * @Author : 逍遥
 * @Date : on 2026/4/27.
 * @Description : 专用于全局控制 Loading、Error 等页面表现
 */
sealed class PageStatus : IUiState{
    data class Loading(val isShow: Boolean = false) : PageStatus()
    data class Error(val error: String = "", val code: Int = 0) : PageStatus()
    data object Completion : PageStatus()
    object IDLE : PageStatus()
}