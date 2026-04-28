package io.github.tmarsteel.flyingnarrator.io

import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2Ghostcar
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.charset.Charset

inline fun ByteBuffer.skipUntil(
    crossinline onEof: (ByteBuffer) -> Unit = {
        throw DR2Ghostcar.InvalidGhostcarFileException("Could not find dynamic offset to read from")
    },
    crossinline condition: (ByteBuffer) -> Boolean,
): Boolean {
    val initialPosition = position()
    for (position in position() .. limit()) {
        val conditionMet = try {
            condition(this)
        } catch (ex: BufferUnderflowException) {
            break
        }
        if (conditionMet) {
            position(position)
            return true
        }
        position(position + 1)
    }

    position(initialPosition)
    onEof(this)
    return false
}

fun ByteBuffer.getVector3(): Vector3 {
    return Vector3(
        getFloat().toDouble(),
        getFloat().toDouble(),
        getFloat().toDouble(),
    )
}

fun ByteBuffer.readDelimitedBigIntAssumeMax64Bits(): ULong {
    val size = get().toUInt()
    return when(size) {
        1u -> get().toULong()
        2u -> getShort().toULong()
        4u -> getInt().toULong()
        8u -> getLong().toULong()
        else -> throw IOException("Unsupported size for big int: $size")
    }
}

fun ByteBuffer.asString(charset: Charset): String {
    val bytes = ByteArray(remaining())
    get(bytes)
    return String(bytes, charset)
}

fun ByteBuffer.readDelimited(): ByteBuffer {
    val length = get().toInt()
    val slice = this.slice(position(), position() + length)
        .limit(length)
        .order(this.order())
    position(position() + length)
    return slice
}

fun ByteBuffer.skipDelimited() {
    val length = get()
    position(position() + length)
}

fun ByteBuffer.skipNBytes(n: Int) {
    position(position() + n)
}

fun InputStream.readLEInt64(): ULong {
    val byte1 = read()
    val byte2 = read()
    val byte3 = read()
    val byte4 = read()
    val byte5 = read()
    val byte6 = read()
    val byte7 = read()
    val byte8 = read()
    if (-1 in listOf(byte1, byte2, byte3, byte4, byte5, byte6, byte7, byte8)) {
        throw EOFException()
    }
    return byte1.toULong() +
        (byte2.toULong() shl 8) +
        (byte3.toULong() shl 16) +
        (byte4.toULong() shl 24) +
        (byte5.toULong() shl 32) +
        (byte6.toULong() shl 40) +
        (byte7.toULong() shl 48) +
        (byte8.toULong() shl 56)
}