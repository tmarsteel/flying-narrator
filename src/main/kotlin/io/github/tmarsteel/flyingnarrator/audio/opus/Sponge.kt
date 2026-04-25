@file:OptIn(ExperimentalAtomicApi::class)
package io.github.tmarsteel.flyingnarrator.audio.opus

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Provides an [java.io.InputStream] and [java.io.OutputStream] that are connected to each other via a [ByteRingBuffer],
 * but without support for separate threads reading and writing.
 */
class Sponge(val capacity: Int) {
    val inputStream: InputStream get()= _inputStream
    val outputStream: OutputStream get()= _outputStream

    private val ringBuffer = ByteRingBuffer(capacity)

    private val thread: AtomicReference<Thread?> = AtomicReference(null)
    private tailrec fun checkThread() {
        val localThread = thread.load()
        if (localThread == null) {
            if (thread.compareAndSet(null, Thread.currentThread())) {
                return
            }
            return checkThread()
        }

        if (localThread != Thread.currentThread()) {
            throw ConcurrentModificationException("Sponge is not thread-safe")
        }
    }

    private val _inputStream = object : InputStream() {
        var closed = false

        private fun checkBeforeUse() {
            if (closed) {
                throw IOException("Stream is closed")
            }
            checkThread()
        }

        override fun read(): Int {
            val dummy = ByteArray(1)
            val n = read(dummy, 0, 1)
            if (n < 0) {
                return -1
            }
            return dummy[0].toInt()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            checkBeforeUse()

            val nBytesToRead = len.coerceAtMost(ringBuffer.remainingRead)
            ringBuffer.get(b, off, nBytesToRead)

            if (nBytesToRead == 0 && _outputStream.closed) {
                return -1
            }

            return nBytesToRead
        }

        override fun available(): Int {
            checkBeforeUse()
            return ringBuffer.remainingRead
        }

        override fun close() {
            closed = true
        }
    }

    private val _outputStream = object : OutputStream() {
        var closed = false

        private fun checkBeforeUse() {
            if (closed) {
                throw IOException("Stream is closed")
            }
            checkThread()
        }

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            checkBeforeUse()
            ringBuffer.put(b, off, len)
        }

        override fun close() {
            closed = true
        }
    }
}