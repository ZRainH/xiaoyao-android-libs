package cn.xiaoyao.bluetooth.exception

/**
 * @Author : 逍遥
 * @Date : on 2026/1/29.
 * @Description :
 */
class BluetoothException(val code : Int, override val message : String) : Exception(message)