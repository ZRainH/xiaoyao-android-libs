package cn.xiaoyao.bluetooth.model

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

/**
 * GATT 特征配置
 */
data class BleCharacteristicProfile(
    val characteristicUuid: UUID,
    val required: Boolean = true,
    val enableNotification: Boolean = false,
    val enableIndication: Boolean = false,
    val writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
) {
    override fun toString(): String {
        return "BleCharacteristicProfile(characteristicUuid=$characteristicUuid, required=$required, enableNotification=$enableNotification, enableIndication=$enableIndication, writeType=$writeType)"
    }
}
