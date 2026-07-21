package cn.xiaoyao.bluetooth.protocol

import android.bluetooth.BluetoothGattCharacteristic
import cn.xiaoyao.bluetooth.model.BleServiceProfile
import java.util.UUID

/**
 * @Author : 逍遥
 * @Date : on 2025/9/10.
 * @Description :
 */

/**
 * 服务解释器接口
 * 负责处理和解析整个服务下所有特征的数据
 */
interface ServiceInterpreter {
    /**
     * 解析特征数据
     * @param characteristic 特征对象
     * @param serviceProfile 所属服务配置
     * @return 解析后的数据对象，可为null
     */
    fun interpret(
        byteArray: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        serviceProfile: BleServiceProfile
    ): Any?

    /**
     * 生成要写入特征的数据
     * @param characteristicUuid 特征UUID
     * @param data 要写入的数据对象
     * @return 转换后的字节数组
     */
    fun generate(
        characteristicUuid: UUID,
        data: Any
    ): ByteArray

    /**
     * 生成消息Id
     */
    fun generateMessageId(data: Any) : String

    /**
     * 设置异步解析回调。
     * 【默认实现为空】：简单的同步解析器无需重写此方法。
     * 只有需要处理粘包、使用后台线程的复杂解析器，才需要重写此方法并保存 listener。
     */
    fun setOnDataParsedListener(listener: ((UUID, Any) -> Unit)?) {
        // 默认什么都不做，子类按需重写
    }

    /**
     *  释放资源
     * 供外部在断开连接时调用，用于关闭解析器内部可能存在的后台线程或清空缓冲区
     */
    fun release() {
        // 默认空实现，子类按需重写
    }
}


/**
 * 默认服务解释器实现
 * 提供基础的数据解析和生成功能
 */
class DefaultServiceInterpreter : ServiceInterpreter {
    override fun interpret(byteArray: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        serviceProfile: BleServiceProfile
    ): Any {
        // 默认返回原始字节数据
        return byteArray
    }

    override fun generate(
        characteristicUuid: UUID,
        data: Any
    ): ByteArray {
        // 默认支持字节数组和字符串类型
        return when (data) {
            is ByteArray -> data
            is String -> data.toByteArray(Charsets.UTF_8)
            else -> throw IllegalArgumentException("不支持的数据类型: ${data.javaClass.simpleName}")
        }
    }

    /**
     * 生成消息Id
     */
    override fun generateMessageId(data: Any): String {
       return ""
    }

}




