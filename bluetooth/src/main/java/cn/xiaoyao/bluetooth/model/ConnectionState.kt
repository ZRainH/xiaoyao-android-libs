package cn.xiaoyao.bluetooth.model

/**
 * @Author : 逍遥
 * @Date : on 2025/9/7.
 * @Description :
 */
sealed class ConnectionState {
    object IDEL : ConnectionState()
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}