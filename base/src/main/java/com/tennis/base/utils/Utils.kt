package com.tennis.base.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.core.content.ContextCompat
import java.util.Locale


/**
 * @Author : 逍遥
 * @Date : on 2025-04-16 10:26.
 * @Description :描述
 */

fun ByteArray.bytesToHexString() : String?{
    return Utils.bytesToHexString(this)
}
fun String.hexToByteArray() : ByteArray?{
    return Utils.hexStringToBytes(this)
}


object Utils {
    fun byteToInt(bb: Byte): Int {
        return bb.toInt() and 0xff
    }
    //小端序（低字节在前，高字节在后）
    fun byteArray2ToIntLE(bytes: ByteArray): Int {
        require(bytes.size == 2)
        return (bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8)
    }
//如果是大端序（高字节在前）
    fun byteArray2ToIntBE(bytes: ByteArray): Int {
        require(bytes.size == 2)
        return ((bytes[0].toInt() and 0xFF) shl 8) or
                (bytes[1].toInt() and 0xFF)
    }

    /**
     * 将byte数组的前4位转换为对应的int值
     *
     * @param byteArray
     * 待转换byte数组
     * @return 转换后int的值
     */
    fun bytesToInt(byteArray: ByteArray): Int {
        var intValue = 0
        requireNotNull(byteArray) { "byte array is null!" }
        require(byteArray.size <= 4) { "byte array size > 4 !" }
        /*for ( int i = 0; i < byteArray.length; i++ ) {
            intValue += ( byteArray[i] & 0xFF ) << ( 8 * ( 3 - i ) );
        }*/
        for (i in byteArray.indices) {
            // 动态计算位移位数：基于数组长度的大端序处理
            val shift = 8 * (byteArray.size - 1 - i)
            intValue = intValue or ((byteArray[i].toInt() and 0xFF) shl shift)
        }

        return intValue
    }


    /**
     * 将int类型的值转换为对应的byte数组
     *
     * @param intValue
     * int值
     * @return 转换后byte数组
     */
    fun intToBytes(intValue: Int): ByteArray {
        val b = ByteArray(4)
        for (i in 0..3) {
            b[i] = (intValue shr 8 * (3 - i) and 0xFF).toByte()
        }
        return b
    }

    /**
     * int转byte数组
     * @param value
     * @return
     */
    fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value ushr 8).toByte(),
            value.toByte()
        )
    }

    /**
     * @功能: 将byte数组转换为16进制字符串
     * @参数: byte数组
     * @结果: 转换得到的字符串
     */
    fun bytesToHexString(src: ByteArray?): String? {
        val stringBuilder = StringBuilder()
        if (src == null || src.size <= 0) {
            return null
        }
        for (i in src.indices) {
            val tmp = src[i].toInt() and 0xFF
            val hv = Integer.toHexString(tmp)
            if (hv.length < 2) {
                stringBuilder.append(0)
            }
            stringBuilder.append(hv)
        }
        return stringBuilder.toString()
    }

    /**
     * 将16进制字符串转换为对应的byte数组
     *
     * @param hexStr 16进制字符串
     * @return 转换得到的byte数组
     */
    fun hexStringToBytes(hexStr: String?): ByteArray? {
        var str: String? = ""
        if (hexStr == null || hexStr == "") {
            return null
        }

        str = hexStr
        if (str.length % 2 == 1) {
            str = "0" + str
        }
        val hexString = str.uppercase(Locale.getDefault())
        val length = hexString.length / 2
        val hexChars = hexString.toCharArray()
        val arrData = ByteArray(length)
        for (i in 0..<length) {
            val pos = i * 2
            arrData[i] =
                (charToByte(hexChars[pos]).toInt() shl 4 or charToByte(hexChars[pos + 1]).toInt()).toByte()
        }
        return arrData
    }

    /**
     * Convert char to byte
     *
     * @param c char
     * @return byte
     */
    private fun charToByte(c: Char): Byte {
        return "0123456789ABCDEF".indexOf(c).toByte()
    }


    /**
     * Modbus 获取报文的KEY字段，以报文的slaveID和cmdCODE字段组合字符串作为key
     */
    fun getMsgKey(data: ByteArray): String? {
        var keysString: String? = ""
        val key = ByteArray(2)
        System.arraycopy(data, 0, key, 0, 2)
        keysString = bytesToHexString(key)
        return keysString
    }

    /**
     * 将short类型转换为byte数组
     *
     * @param shortValue 待转换short数值
     * @return 转换后byte数组
     */
    fun shortToBytes(shortValue: Short): ByteArray {
        val b = ByteArray(2)
        for (i in 0..1) {
            b[i] = (shortValue.toInt() shr (i * 8)).toByte()
        }
        return b
    }


    fun getCRC2(bytes: ByteArray): ByteArray {
        var crc = 0xFFFF
        for (b in bytes) {
            crc = crc xor (b.toInt() and 0xFF)
            for (i in 0..7) {
                if ((crc and 0x0001) != 0) {
                    crc = crc shr 1
                    crc = crc xor 0xA001
                } else {
                    crc = crc shr 1
                }
            }
        }
        // 将 CRC 值拆分为两个字节
        val crcBytes = ByteArray(2)
        crcBytes[0] = (crc and 0xFF).toByte()
        crcBytes[1] = ((crc shr 8) and 0xFF).toByte()
        return crcBytes
    }

    fun getMipmap(context :Context ,pngName: String?): Drawable? {
        // 获取资源 ID（例如：R.mipmap.ic_launcher）
        if (TextUtils.isEmpty(pngName)) {
            return null
        }
        val resId =
            context.resources.getIdentifier(pngName, "mipmap", context.packageName)
        // 检查资源是否存在
        return if (resId != 0) {
            ContextCompat.getDrawable(context, resId)
        } else {
            null
        }
    }

    fun combineLow7Bits(byte1: Int, byte2: Int): Int {
        val low7Bits1 = byte1 and 0x7F  // 取低7位 (0x7F = 0b01111111)
        val low7Bits2 = byte2 and 0x7F  // 取低7位
        return (low7Bits1 shl 7) or low7Bits2  // 组合成14位数据
    }

}
