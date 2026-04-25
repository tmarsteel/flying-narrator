package io.github.tmarsteel.flyingnarrator.audio.opus

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.AutoCloseable

/**
 * Creates new [InputStream]s from a given [InputStream] that supports infinite mark+reset,
 * e.g. [java.io.ByteArrayInputStream].
 */
class InfiniteResetStreamReplicator(
    private val streamSupportingInfiniteReset: InputStream,
) : () -> InputStream, AutoCloseable {
    init {
        streamSupportingInfiniteReset.mark(Int.MAX_VALUE)
    }

    private var closed = false

    private fun checkNotClosed() {
        if (closed) {
            throw IOException("Stream factory closed")
        }
    }

    private var currentUnclosedChildSream: InputStream? = null
    override fun invoke(): InputStream {
        checkNotClosed()
        if (currentUnclosedChildSream != null) {
            throw ConcurrentModificationException("Cannot create multiple streams simultaneously, close the sub-streams first")
        }

        streamSupportingInfiniteReset.reset()
        streamSupportingInfiniteReset.mark(Int.MAX_VALUE)

        val nextStream = object : InputStream() {
            private fun checkNotClosed() {
                if (currentUnclosedChildSream !== this) {
                    throw IOException("Stream is closed")
                }
            }

            override fun available(): Int {
                checkNotClosed()
                return streamSupportingInfiniteReset.available()
            }

            override fun read(b: ByteArray): Int {
                checkNotClosed()
                return streamSupportingInfiniteReset.read(b)
            }

            override fun readAllBytes(): ByteArray {
                checkNotClosed()
                return streamSupportingInfiniteReset.readAllBytes()
            }

            override fun readNBytes(len: Int): ByteArray {
                checkNotClosed()
                return streamSupportingInfiniteReset.readNBytes(len)
            }

            override fun readNBytes(b: ByteArray, off: Int, len: Int): Int {
                checkNotClosed()
                return streamSupportingInfiniteReset.readNBytes(b, off, len)
            }

            override fun skip(n: Long): Long {
                checkNotClosed()
                return streamSupportingInfiniteReset.skip(n)
            }

            override fun skipNBytes(n: Long) {
                checkNotClosed()
                streamSupportingInfiniteReset.skipNBytes(n)
            }

            override fun transferTo(out: OutputStream): Long {
                checkNotClosed()
                return streamSupportingInfiniteReset.transferTo(out)
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                checkNotClosed()
                return streamSupportingInfiniteReset.read(b, off, len)
            }

            override fun read(): Int {
                checkNotClosed()
                return streamSupportingInfiniteReset.read()
            }

            override fun markSupported(): Boolean {
                return false
            }

            override fun mark(readlimit: Int) {
                throw IOException("mark/reset not supported on sub-streams")
            }

            override fun reset() {
                throw IOException("mark/reset not supported on sub-streams")
            }

            override fun close() {
                currentUnclosedChildSream = null
            }
        }

        currentUnclosedChildSream = nextStream
        return nextStream
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        currentUnclosedChildSream = null
        streamSupportingInfiniteReset.reset()
    }

    companion object {
        fun supports(stream: InputStream): Boolean {
            return stream is ByteArrayInputStream
        }
    }
}