package cn.xiaoyao.bluetooth.model

/**
 * 蓝牙设备信息
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val serviceProfiles: List<BleServiceProfile> = emptyList(),
    val lastConnectedTime: Long = System.currentTimeMillis(),
    val connectMode: ConnectMode = ConnectMode.AUTO_RECONNECT
)
