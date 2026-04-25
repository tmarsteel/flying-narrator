package io.github.tmarsteel.flyingnarrator.audio.opus

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.nio.BufferOverflowException
import java.util.Arrays
import kotlin.concurrent.thread

class SpongeTest : FreeSpec({
    "uncongested" {
        val sponge = Sponge(10)
        val buffer = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        sponge.outputStream.write(buffer)
        Arrays.fill(buffer, 0)
        sponge.inputStream.read(buffer) shouldBe 10
        buffer shouldBe byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    }

    "writing over capacity" {
        val sponge = Sponge(1)
        val buffer = byteArrayOf(1, 2)
        shouldThrow<BufferOverflowException> {
            sponge.outputStream.write(buffer)
        }
    }

    "reading before any written" {
        val sponge = Sponge(10)
        val buffer = byteArrayOf(1, 2)
        sponge.inputStream.read(buffer) shouldBe 0
    }

    "closing ouput makes input return -1 instead of 0" {
        val sponge = Sponge(10)
        val buffer = byteArrayOf(1, 2)
        sponge.inputStream.read(buffer) shouldBe 0
        sponge.outputStream.close()
        sponge.inputStream.read(buffer) shouldBe -1
    }

    "multithread throws ConcurrentModificationException" {
        val sponge = Sponge(10)
        sponge.outputStream.write(byteArrayOf(1, 2))
        thread {
            shouldThrow<ConcurrentModificationException> {
                sponge.inputStream.read(ByteArray(10))
            }
            shouldThrow<ConcurrentModificationException> {
                sponge.outputStream.write(ByteArray(10))
            }
        }
    }
})