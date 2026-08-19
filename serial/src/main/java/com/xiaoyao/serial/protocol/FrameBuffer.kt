package com.xiaoyao.serial.protocol

/**
 * 可增长字节缓冲：追加 / 按前缀丢弃均为单次 arraycopy，避免 `ArrayList<Byte>` 装箱与 `removeAt(0)` 的 O(N²)。
 */
internal class FrameBuffer(initialCapacity: Int = 256) {
    private var data = ByteArray(initialCapacity.coerceAtLeast(16))
    private var length = 0

    val size: Int get() = length

    fun clear() {
        length = 0
    }

    fun append(chunk: ByteArray) {
        if (chunk.isEmpty()) return
        ensureCapacity(length + chunk.size)
        System.arraycopy(chunk, 0, data, length, chunk.size)
        length += chunk.size
    }

    fun append(byte: Byte) {
        ensureCapacity(length + 1)
        data[length++] = byte
    }

    operator fun get(index: Int): Byte {
        if (index < 0 || index >= length) throw IndexOutOfBoundsException("$index of $length")
        return data[index]
    }

    fun copyOfRange(start: Int, end: Int): ByteArray {
        require(start in 0..end && end <= length) { "invalid range $start..$end of $length" }
        return data.copyOfRange(start, end)
    }

    /** 丢弃前 [count] 字节，剩余前移一次完成 */
    fun discardFront(count: Int) {
        if (count <= 0) return
        if (count >= length) {
            length = 0
            return
        }
        val remain = length - count
        System.arraycopy(data, count, data, 0, remain)
        length = remain
    }

    fun endsWith(suffix: ByteArray): Boolean {
        if (suffix.isEmpty() || length < suffix.size) return false
        val start = length - suffix.size
        for (i in suffix.indices) {
            if (data[start + i] != suffix[i]) return false
        }
        return true
    }

    fun indexOf(pattern: ByteArray): Int {
        if (pattern.isEmpty() || length < pattern.size) return -1
        val lastStart = length - pattern.size
        outer@ for (i in 0..lastStart) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= data.size) return
        var cap = data.size
        while (cap < needed) cap = cap * 2
        data = data.copyOf(cap)
    }
}
