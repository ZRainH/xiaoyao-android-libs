package com.xiaoyao.serial.connection

import com.xiaoyao.serial.exception.SerialErrorCode
import com.xiaoyao.serial.exception.SerialException
import com.xiaoyao.serial.model.SerialConfig
import com.xiaoyao.serial.protocol.RawProtocolAdapter
import com.xiaoyao.serial.transport.SerialTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SerialExecuteTest {

    @Test
    fun `execute matches response by matcher`() = runBlocking {
        val transport = LoopbackTransport()
        val conn = SerialConnection(
            connectionId = "t1",
            transport = transport,
            config = SerialConfig(readTimeoutMs = 50, executeTimeoutMs = 1000),
            protocol = RawProtocolAdapter
        )
        try {
            val job = async {
                conn.execute(byteArrayOf(0x01, 0x02), timeoutMs = 1000) { frame ->
                    frame.size >= 2 && frame[0] == 0x01.toByte() && frame[1] == 0x99.toByte()
                }
            }
            delay(80)
            transport.inject(byteArrayOf(0x01, 0x99.toByte()))
            val result = job.await()
            assertTrue(result.isSuccess)
            assertArrayEquals(byteArrayOf(0x01, 0x99.toByte()), result.getOrThrow())
        } finally {
            conn.close()
        }
    }

    @Test
    fun `execute times out when no match`() = runBlocking {
        val transport = LoopbackTransport()
        val conn = SerialConnection(
            connectionId = "t2",
            transport = transport,
            config = SerialConfig(readTimeoutMs = 30, executeTimeoutMs = 200),
            protocol = RawProtocolAdapter
        )
        try {
            val result = conn.execute(byteArrayOf(0x01), timeoutMs = 200) { false }
            assertTrue(result.isFailure)
            assertEquals(SerialErrorCode.TIMEOUT, (result.exceptionOrNull() as SerialException).code)
        } finally {
            conn.close()
        }
    }

    @Test
    fun `enqueue invokes callback with matched response`() {
        val transport = LoopbackTransport()
        val conn = SerialConnection(
            connectionId = "t3",
            transport = transport,
            config = SerialConfig(readTimeoutMs = 50, executeTimeoutMs = 1000),
            protocol = RawProtocolAdapter
        )
        try {
            val latch = CountDownLatch(1)
            val resultRef = AtomicReference<Result<ByteArray>>()
            conn.enqueue(
                request = byteArrayOf(0x01),
                timeoutMs = 1000,
                matcher = { frame ->
                    frame.size >= 2 && frame[0] == 0x01.toByte() && frame[1] == 0x99.toByte()
                },
                onComplete = { result ->
                    resultRef.set(result)
                    latch.countDown()
                }
            )
            Thread.sleep(80)
            transport.inject(byteArrayOf(0x01, 0x99.toByte()))
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            val result = resultRef.get()
            assertTrue(result.isSuccess)
            assertArrayEquals(byteArrayOf(0x01, 0x99.toByte()), result.getOrThrow())
        } finally {
            conn.close()
        }
    }

    private class LoopbackTransport : SerialTransport {
        private val rx = LinkedBlockingQueue<ByteArray>()
        private val writeCount = AtomicInteger(0)
        @Volatile private var open = true
        override val name: String = "loopback-${writeCount.get()}"
        override fun isOpen(): Boolean = open
        override fun write(data: ByteArray, timeoutMs: Int) {
            writeCount.incrementAndGet()
        }
        override fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val chunk = rx.poll(timeoutMs.toLong().coerceAtLeast(1L), TimeUnit.MILLISECONDS) ?: return 0
            val n = minOf(chunk.size, buffer.size)
            System.arraycopy(chunk, 0, buffer, 0, n)
            return n
        }
        override fun close() {
            open = false
        }
        fun inject(bytes: ByteArray) {
            rx.offer(bytes)
        }
    }
}
