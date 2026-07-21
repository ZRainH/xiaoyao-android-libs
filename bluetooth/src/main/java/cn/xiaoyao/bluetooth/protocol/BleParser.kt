package cn.xiaoyao.bluetooth.protocol

import android.bluetooth.BluetoothGattService
import java.util.UUID

/**
 * @Author : 逍遥
 * @Date : on 2025/9/10.
 * @Description :
 */
// 解析器接口 - 专注于服务解析
interface BleServiceParser {
    fun parse(service: BluetoothGattService): Any?
}

// 默认服务解析器
class DefaultServiceParser : BleServiceParser {
    override fun parse(service: BluetoothGattService): Any? {
        return service.characteristics.associate {
            it.uuid to it.value
        }
    }
}

// 解析器注册表 - 集中管理解析器
object ParserRegistry {
    private val parsers = mutableMapOf<UUID, BleServiceParser>()

    // 注册解析器
    fun register(uuid: UUID, parser: BleServiceParser) {
        parsers[uuid] = parser
    }

    // 获取解析器，默认返回默认解析器
    fun getParser(uuid: UUID): BleServiceParser {
        return parsers[uuid] ?: DefaultServiceParser()
    }

    // 初始化常用解析器
    fun initializeDefaultParsers() {
        // 可以添加更多默认解析器
    }
}
