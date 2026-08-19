package com.xiaoyao.serial.connection

import com.xiaoyao.serial.SerialPortManager
import com.xiaoyao.serial.exception.SerialErrorCode
import com.xiaoyao.serial.exception.SerialException
import com.xiaoyao.serial.model.SerialConfig
import com.xiaoyao.serial.model.SerialConnectionState
import com.xiaoyao.serial.protocol.RawProtocolAdapter
import com.xiaoyao.serial.transport.SerialTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SerialReliabilityTest {

    @Test
    fun `read io failure moves state to ERROR and closes`() = runBlocking {
        val transport = ControllableTransport()
        val conn = SerialConnection(
            connectionId = "r1",
            transport = transport,
            config = SerialConfig(readTimeoutMs = 30),
            protocol = RawProtocolAdapter
        )
        try {
            delay(40)
            transport.failNextRead(IOException("device gone"))
            delay(120)
            assertEquals(SerialConnectionState.ERROR, conn.state.value)
            assertFalse(conn.isConnected())
            assertFalse(transport.isOpen())
        } finally {
            conn.close()
        }
    }

    @Test
    fun `write failure returns Result and closes session`() = runBlocking {
        val transport = ControllableTransport()
        transport.failWrites = true
        val conn = SerialConnection(
            connectionId = "w1",
            transport = transport,
            config = SerialConfig(readTimeoutMs = 30),
            protocol = RawProtocolAdapter
        )
        try {
            val result = conn.write(byteArrayOf(0x01))
            assertTrue(result.isFailure)
            assertEquals(SerialErrorCode.WRITE_FAILED, (result.exceptionOrNull() as SerialException).code)
            assertEquals(SerialConnectionState.ERROR, conn.state.value)
        } finally {
            conn.close()
        }
    }

    @Test
    fun `write when disconnected returns failure not throw`() = runBlocking {
        val transport = ControllableTransport()
        val conn = SerialConnection(
            connectionId = "w2",
            transport = transport,
            config = SerialConfig(readTimeoutMs = 30),
            protocol = RawProtocolAdapter
        )
        conn.close()
        val result = conn.write(byteArrayOf(0x01))
        assertTrue(result.isFailure)
        assertEquals(SerialErrorCode.NOT_CONNECTED, (result.exceptionOrNull() as SerialException).code)
    }

    @Test
    fun `manager removes connection after self close`() = runBlocking {
        val manager = SerialPortManager.getInstance()
        manager.closeAll()
        val id = "mgr-self-close-${System.nanoTime()}"
        val transport = ControllableTransport(name = id)
        val opened = manager.open(id, transport, SerialConfig(readTimeoutMs = 30))
        assertTrue(opened.isSuccess)
        delay(40)
        transport.failNextRead(IOException("unplug"))
        delay(150)
        assertNull(manager.getConnection(id))
        assertFalse(manager.connectionsSnapshot.value.containsKey(id))
        assertFalse(manager.connectionStates().containsKey(id))
    }

    private class ControllableTransport(
        override val name: String = "ctrl-${System.nanoTime()}"
    ) : SerialTransport {
        private val rx = LinkedBlockingQueue<ByteArray>()
        private val open = AtomicBoolean(true)
        private val readFail = LinkedBlockingQueue<Exception>()
        @Volatile var failWrites = false
        private val writeCount = AtomicInteger(0)

        override fun isOpen(): Boolean = open.get()

        override fun write(data: ByteArray, timeoutMs: Int) {
            if (!open.get()) throw SerialException(SerialErrorCode.NOT_CONNECTED, "closed")
            if (failWrites) {
                throw SerialException(SerialErrorCode.WRITE_FAILED, "write boom")
            }
            writeCount.incrementAndGet()
        }

        override fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val fail = readFail.poll()
            if (fail != null) throw fail
            if (!open.get()) {
                throw SerialException(SerialErrorCode.NOT_CONNECTED, "closed")
            }
            val chunk = rx.poll(timeoutMs.toLong().coerceAtLeast(1L), TimeUnit.MILLISECONDS) ?: return 0
            val n = minOf(chunk.size, buffer.size)
            System.arraycopy(chunk, 0, buffer, 0, n)
            return n
        }

        override fun close() {
            open.set(false)
        }

        fun failNextRead(error: Exception) {
            readFail.offer(error)
        }
    }
}
