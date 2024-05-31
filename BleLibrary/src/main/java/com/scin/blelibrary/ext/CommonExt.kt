package com.scin.blelibrary.ext

import com.scin.blelibrary.utils.CrcUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nordicsemi.android.common.core.DataByteArray
import java.math.BigDecimal
import java.math.RoundingMode


/**
 * 16进制字符串转字节数组
 */
fun String.strToByteArray(): ByteArray {
    val len = this.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(
            this[i + 1], 16
        )).toByte()
        i += 2
    }
    return data
}
/**
 * 字节数组转16进制字符串
 */
fun ByteArray.toHexString(): String {
    val hex = "0123456789abcdef"
    val sb: StringBuilder = StringBuilder(this.size * 2)
    for (aByte in this) { // 取出这个字节的高4位，然后与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
        sb.append(
            hex[(aByte.toInt() shr 4) and 0x0f]
        ) // 取出这个字节的低位，与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
        sb.append(hex[aByte.toInt() and 0x0f])
    }
    return sb.toString()
}

/**
 * 以大端模式将字节数组转成int
 */
fun ByteArray.toBigInt(): Int {
    val str = this.toHexString()
    return Integer.parseUnsignedInt(str, 16)
}

/**
 * 以大端模式将int转成字节数组
 */
fun Int.toBytesBig(size: Int): ByteArray {
    val src = ByteArray(size)
    for (i in 0 until size) {
        src[i] = (this shr ((size - i - 1) * 8) and 0xFF).toByte()
    }
    return src
}

/**
 * 以大端模式将int转成字节数组
 */
fun Long.toBytesBig(size: Int): ByteArray {
    val src = ByteArray(size)
    for (i in 0 until size) {
        src[i] = (this shr ((size - i - 1) * 8) and 0xFF).toByte()
    }
    return src
}

fun Int.toBytesBigArr(size: Int): Array<Byte> {
    return this.toBytesBig(size).toTypedArray()
}

fun Long.toBytesBigArr(size: Int): Array<Byte> {
    return this.toBytesBig(size).toTypedArray()
}

fun Int.toBigDecimal(divisor: Int = 100, scale: Int = 2): BigDecimal {
    return BigDecimal(this).divide(BigDecimal(divisor), scale, RoundingMode.HALF_UP)
}

/**
 * 数字字节位校验
 */
fun Int.bitHave(value: Int): Boolean {
    return this and value != 0
}

/**
 * 数字字节位改动
 */
fun Int.bitChange(value: Int): Int {
    return when {
        this and value != 0 -> {
            this xor value
        }

        else -> {
            this or value
        }
    }
}

/**
 * 数字字节位改动
 */
fun Int.bitRemove(vararg value: Int): Int {
    var result = this
    value.forEach {
        result = when {
            result and it != 0 -> {
                result xor it
            }

            else -> {
                result
            }
        }
    }
    return result
}

/**
 * 数字字节位改动(返回特定值)
 */
fun Boolean.bitCheck(have: Int, no: Int): Int {
    return if (this) have else no
}

fun Int.bitHaveCount(bit: Int): Int {
    var count = 0
    repeat(bit * 8) {
        val result = this and (1 shl it) != 0
        if (result) {
            count++
        }
    }
    return count
}

fun Int.bitHaveData(length: Int): ArrayList<Int> {
    val bitData = arrayListOf<Int>()
    repeat(length * 8) {
        val result = this and (1 shl it) != 0
        if (result) {
            bitData.add(it)
        }
    }
    return bitData
}


fun Int.getBitByIndex(index: Int): Int {
    return if (this and (1 shl index) != 0) {
        1
    } else {
        0
    }
}

fun Double?.toMultiplyInt(multiply: Int): Int {
    return this?.let {
        this * multiply
    }?.toInt() ?: 0
}

fun ByteArray.fillingDeal(srcIndex: Int, length: Int, value: Int) {
    for (i in srcIndex until srcIndex + length) {
        this[i] = (value shr 8 * (length - (i - srcIndex) - 1) and 0xff).toByte()
    }
}

fun ByteArray.v1Crc16(length: Int): Int {
    return CrcUtils.ccbCalcCrc16(this, length)
}