package com.xiaoyao.modbus

import com.xiaoyao.modbus.exception.ModbusException
import com.xiaoyao.modbus.frame.ModbusFrameBuilder
import com.xiaoyao.modbus.frame.ModbusFrameParser
import com.xiaoyao.modbus.model.ModbusFunctionCode
import com.xiaoyao.modbus.model.ModbusMasterConfig
import com.xiaoyao.modbus.model.ModbusRequestContext
import com.xiaoyao.modbus.transport.ModbusTransport
import com.xiaoyao.modbus.transport.SerialModbusTransport
import com.xiaoyao.serial.connection.SerialConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Modbus RTU **主站**入口（可靠性增强）。
 *
 * 在字段匹配之外额外提供：
 * - 可配置重试（超时/CRC/错帧/校验失败）
 * - 写后读回校验（[ModbusMasterConfig.verifyWrites]）
 * - 与传输层配合的发前/收后静默清缓冲
 */
class ModbusMaster(
    private val transport: ModbusTransport,
    var slaveId: Int = 1,
    var timeoutMs: Long = 1000L,
    var config: ModbusMasterConfig = ModbusMasterConfig.DEFAULT
) {
    private val mutex = Mutex()

    constructor(
        connection: SerialConnection,
        slaveId: Int = 1,
        baudRate: Int = 9600,
        timeoutMs: Long = 1000L,
        config: ModbusMasterConfig = ModbusMasterConfig.DEFAULT
    ) : this(
        transport = SerialModbusTransport(
            connection = connection,
            baudRate = baudRate,
            preSendSettleMs = config.preSendSettleMs,
            postReceiveSettleMs = config.postReceiveSettleMs
        ),
        slaveId = slaveId,
        timeoutMs = timeoutMs,
        config = config
    )

    /** 功能码 01：读线圈 */
    suspend fun readCoils(address: Int, quantity: Int): Result<BooleanArray> =
        mutex.withLock { executeWithRetry { readBitsOnce(ModbusFunctionCode.READ_COILS, address, quantity) } }

    /** 功能码 02：读离散量输入 */
    suspend fun readDiscreteInputs(address: Int, quantity: Int): Result<BooleanArray> =
        mutex.withLock {
            executeWithRetry { readBitsOnce(ModbusFunctionCode.READ_DISCRETE_INPUTS, address, quantity) }
        }

    /** 功能码 03：读保持寄存器 */
    suspend fun readHoldingRegisters(address: Int, quantity: Int): Result<IntArray> =
        mutex.withLock {
            executeWithRetry {
                readRegistersOnce(ModbusFunctionCode.READ_HOLDING_REGISTERS, address, quantity)
            }
        }

    /** 功能码 04：读输入寄存器 */
    suspend fun readInputRegisters(address: Int, quantity: Int): Result<IntArray> =
        mutex.withLock {
            executeWithRetry {
                readRegistersOnce(ModbusFunctionCode.READ_INPUT_REGISTERS, address, quantity)
            }
        }

    /**
     * 功能码 05：写单个线圈。
     * [config.verifyWrites] 为 true 时，写成功后再用 01 读回比对。
     */
    suspend fun writeSingleCoil(address: Int, on: Boolean): Result<Unit> = mutex.withLock {
        executeWithRetry {
            val ctx = ModbusRequestContext.writeSingleCoil(slaveId, address, on)
            val request = ModbusFrameBuilder.writeSingleCoil(slaveId, address, on)
            val response = transport.transact(request, ctx, timeoutMs)
            ModbusFrameParser.ensureValid(response, slaveId, ModbusFunctionCode.WRITE_SINGLE_COIL)
            if (config.verifyWrites) {
                val readBack = readBitsOnce(ModbusFunctionCode.READ_COILS, address, 1)
                if (readBack[0] != on) {
                    throw ModbusException(
                        ModbusException.VERIFY_MISMATCH,
                        "coil verify failed: addr=$address expect=$on actual=${readBack[0]}"
                    )
                }
            }
        }
    }

    /**
     * 功能码 06：写单个寄存器。
     * [config.verifyWrites] 为 true 时，写成功后再用 03 读回比对。
     */
    suspend fun writeSingleRegister(address: Int, value: Int): Result<Unit> = mutex.withLock {
        executeWithRetry {
            val ctx = ModbusRequestContext.writeSingleRegister(slaveId, address, value)
            val request = ModbusFrameBuilder.writeSingleRegister(slaveId, address, value)
            val response = transport.transact(request, ctx, timeoutMs)
            ModbusFrameParser.ensureValid(response, slaveId, ModbusFunctionCode.WRITE_SINGLE_REGISTER)
            if (config.verifyWrites) {
                val readBack = readRegistersOnce(ModbusFunctionCode.READ_HOLDING_REGISTERS, address, 1)
                val expect = value and 0xFFFF
                if (readBack[0] != expect) {
                    throw ModbusException(
                        ModbusException.VERIFY_MISMATCH,
                        "register verify failed: addr=$address expect=$expect actual=${readBack[0]}"
                    )
                }
            }
        }
    }

    /**
     * 功能码 15：写多个线圈；可选写后按范围读回比对。
     */
    suspend fun writeMultipleCoils(address: Int, values: BooleanArray): Result<Unit> = mutex.withLock {
        executeWithRetry {
            val ctx = ModbusRequestContext.writeMultipleCoils(slaveId, address, values.size)
            val request = ModbusFrameBuilder.writeMultipleCoils(slaveId, address, values)
            val response = transport.transact(request, ctx, timeoutMs)
            ModbusFrameParser.ensureValid(response, slaveId, ModbusFunctionCode.WRITE_MULTIPLE_COILS)
            if (config.verifyWrites) {
                val readBack = readBitsOnce(ModbusFunctionCode.READ_COILS, address, values.size)
                if (!readBack.contentEquals(values)) {
                    throw ModbusException(
                        ModbusException.VERIFY_MISMATCH,
                        "coils verify failed: addr=$address quantity=${values.size}"
                    )
                }
            }
        }
    }

    /**
     * 功能码 16：写多个寄存器；可选写后按范围读回比对。
     */
    suspend fun writeMultipleRegisters(address: Int, values: IntArray): Result<Unit> = mutex.withLock {
        executeWithRetry {
            val normalized = IntArray(values.size) { values[it] and 0xFFFF }
            val ctx = ModbusRequestContext.writeMultipleRegisters(slaveId, address, normalized.size)
            val request = ModbusFrameBuilder.writeMultipleRegisters(slaveId, address, normalized)
            val response = transport.transact(request, ctx, timeoutMs)
            ModbusFrameParser.ensureValid(response, slaveId, ModbusFunctionCode.WRITE_MULTIPLE_REGISTERS)
            if (config.verifyWrites) {
                val readBack = readRegistersOnce(
                    ModbusFunctionCode.READ_HOLDING_REGISTERS,
                    address,
                    normalized.size
                )
                if (!readBack.contentEquals(normalized)) {
                    throw ModbusException(
                        ModbusException.VERIFY_MISMATCH,
                        "registers verify failed: addr=$address quantity=${normalized.size}"
                    )
                }
            }
        }
    }

    /** 无外层锁的读位实现，供写后校验复用，避免死锁 */
    private suspend fun readBitsOnce(function: Int, address: Int, quantity: Int): BooleanArray {
        val ctx = ModbusRequestContext.readBits(slaveId, function, address, quantity)
        val request = ModbusFrameBuilder.readBits(slaveId, function, address, quantity)
        val response = transport.transact(request, ctx, timeoutMs)
        ModbusFrameParser.ensureValid(response, slaveId, function)
        return ModbusFrameParser.parseReadBits(response, quantity)
    }

    /** 无外层锁的读寄存器实现 */
    private suspend fun readRegistersOnce(function: Int, address: Int, quantity: Int): IntArray {
        val ctx = ModbusRequestContext.readRegisters(slaveId, function, address, quantity)
        val request = ModbusFrameBuilder.readRegisters(slaveId, function, address, quantity)
        val response = transport.transact(request, ctx, timeoutMs)
        ModbusFrameParser.ensureValid(response, slaveId, function)
        return ModbusFrameParser.parseReadRegisters(response, quantity)
    }

    /**
     * 带重试执行。
     * 可重试：TIMEOUT / CRC_ERROR / INVALID_RESPONSE / TRANSPORT_ERROR / VERIFY_MISMATCH（可关）
     * 不重试：EXCEPTION_RESPONSE（从站明确拒绝）、INVALID_ARGUMENT
     */
    private suspend fun <T> executeWithRetry(block: suspend () -> T): Result<T> {
        var attempt = 0
        var lastError: ModbusException? = null
        val totalAttempts = 1 + config.maxRetries.coerceAtLeast(0)

        while (attempt < totalAttempts) {
            try {
                return Result.success(block())
            } catch (e: ModbusException) {
                lastError = e
                val canRetry = when (e.code) {
                    ModbusException.TIMEOUT,
                    ModbusException.CRC_ERROR,
                    ModbusException.INVALID_RESPONSE,
                    ModbusException.TRANSPORT_ERROR -> true
                    ModbusException.VERIFY_MISMATCH -> config.retryOnVerifyMismatch
                    else -> false
                }
                attempt++
                if (!canRetry || attempt >= totalAttempts) {
                    return Result.failure(e)
                }
                if (config.retryDelayMs > 0) {
                    delay(config.retryDelayMs.milliseconds)
                }
            } catch (e: Exception) {
                val wrapped = ModbusException(
                    ModbusException.TRANSPORT_ERROR,
                    e.message ?: "modbus failed",
                    cause = e
                )
                lastError = wrapped
                attempt++
                if (attempt >= totalAttempts) {
                    return Result.failure(wrapped)
                }
                if (config.retryDelayMs > 0) {
                    delay(config.retryDelayMs.milliseconds)
                }
            }
        }
        return Result.failure(
            lastError ?: ModbusException(ModbusException.TRANSPORT_ERROR, "modbus failed")
        )
    }
}
