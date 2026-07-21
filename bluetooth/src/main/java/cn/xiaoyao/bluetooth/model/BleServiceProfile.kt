package cn.xiaoyao.bluetooth.model

import cn.xiaoyao.bluetooth.protocol.DefaultServiceInterpreter
import cn.xiaoyao.bluetooth.protocol.ServiceInterpreter
import java.util.UUID

/**
 * 服务与特征的映射关系模型
 */
class BleServiceProfile(
    val serviceUuid: UUID,
    val required: Boolean = true,
    val characteristics: List<BleCharacteristicProfile>,
    val serviceInterpreter: ServiceInterpreter = DefaultServiceInterpreter()
)
