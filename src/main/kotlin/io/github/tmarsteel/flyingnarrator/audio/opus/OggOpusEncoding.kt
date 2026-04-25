package io.github.tmarsteel.flyingnarrator.audio.opus

import tomp2p.opuswrapper.Opus
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

class OggOpusEncoding(
    val application: Application = Application.UNSPECIFIED,
    val signal: Signal = Signal.UNSPECIFIED,
    val complexity: Int = 10,
    val bitsPerSecond: Int = 128000,
    val opusFrameSize: Duration = 20.milliseconds,
    val maxEncodedFrameSizeInBytes: Int = Int.MAX_VALUE,
) : AudioFormat.Encoding("OPUS") {
    init {
        require(complexity in 0..10 || complexity == COMPLEXITY_UNSPECIFIED) {
            "complexity must be between 0 and 10, or ${::COMPLEXITY_UNSPECIFIED.name}"
        }
        require(bitsPerSecond > 0 || bitsPerSecond == COMPLEXITY_UNSPECIFIED) {
            "bitsPerSecond must be greater than 0"
        }
        require(opusFrameSize in SUPPORTED_OPUS_FRAME_SIZES || opusFrameSize == FRAME_SIZE_UNSPECIFIED) {
            "frameSize must be one of ${SUPPORTED_OPUS_FRAME_SIZES}, or ${::FRAME_SIZE_UNSPECIFIED.name}"
        }
        require(maxEncodedFrameSizeInBytes > 0 || maxEncodedFrameSizeInBytes == MAX_ENCODED_FRAME_SIZE_UNSPECIFIED) {
            "maxEncodedFrameSizeInBytes must be greater than 0, or ${::MAX_ENCODED_FRAME_SIZE_UNSPECIFIED.name}"
        }
    }

    fun concretizeForEncoding(): OggOpusEncoding {
        val application = this.application.takeUnless { it == Application.UNSPECIFIED } ?: DEFAULT_APPLICATION
        val signal = this.signal.takeUnless { it == Signal.UNSPECIFIED } ?: DEFAULT_SIGNAL
        val complexity = this.complexity.takeUnless { it == COMPLEXITY_UNSPECIFIED } ?: DEFAULT_COMPLEXITY
        val bitsPerSecond = this.bitsPerSecond.takeUnless { it == BITS_PER_SECOND_UNSPECIFIED } ?: DEFAULT_BITS_PER_SECOND
        val frameSize = this.opusFrameSize.takeUnless { it == FRAME_SIZE_UNSPECIFIED } ?: DEFAULT_FRAME_SIZE
        val maxEncodedFrameSizeInBytes = this.maxEncodedFrameSizeInBytes.takeUnless { it == MAX_ENCODED_FRAME_SIZE_UNSPECIFIED } ?: DEFAULT_ENCODED_MAX_FRAME_SIZE
        return OggOpusEncoding(application, signal, complexity, bitsPerSecond, frameSize, maxEncodedFrameSizeInBytes)
    }

    enum class Application(val value: Int) {
        VOIP(Opus.OPUS_APPLICATION_VOIP),
        AUDIO(Opus.OPUS_APPLICATION_AUDIO),
        RESTRICTED_LOW_DELAY(Opus.OPUS_APPLICATION_RESTRICTED_LOWDELAY),
        UNSPECIFIED(-1),
    }

    enum class Signal(val value: Int) {
        AUTO(Opus.OPUS_AUTO),
        VOICE(Opus.OPUS_SIGNAL_VOICE),
        MUSIC(Opus.OPUS_SIGNAL_MUSIC),
        UNSPECIFIED(-1),
    }

    companion object {
        @JvmStatic
        val COMPLEXITY_UNSPECIFIED = -1

        @JvmStatic
        val BITS_PER_SECOND_UNSPECIFIED = -1

        @JvmStatic
        val FRAME_SIZE_UNSPECIFIED = Duration.ZERO

        @JvmStatic
        val MAX_ENCODED_FRAME_SIZE_UNSPECIFIED = -1

        @JvmStatic
        val SUPPORTED_OPUS_FRAME_SIZES = setOf(
            2500.microseconds,
            5.milliseconds,
            10.milliseconds,
            20.milliseconds,
            40.milliseconds,
            60.milliseconds,
        )

        @JvmStatic
        val DEFAULT_APPLICATION = Application.AUDIO

        @JvmStatic
        val DEFAULT_SIGNAL = Signal.AUTO

        @JvmStatic
        val DEFAULT_FRAME_SIZE = 20.milliseconds

        @JvmStatic
        val DEFAULT_ENCODED_MAX_FRAME_SIZE = Int.MAX_VALUE

        @JvmStatic
        val DEFAULT_COMPLEXITY = 10

        @JvmStatic
        val DEFAULT_BITS_PER_SECOND = 128_000

        @JvmStatic
        val OPUS_NOT_FURTHER_SPECIFIED = OggOpusEncoding(
            Application.UNSPECIFIED,
            Signal.UNSPECIFIED,
            COMPLEXITY_UNSPECIFIED,
            BITS_PER_SECOND_UNSPECIFIED,
            FRAME_SIZE_UNSPECIFIED,
            MAX_ENCODED_FRAME_SIZE_UNSPECIFIED,
        )

        init {
            try {
                OPUS_NOT_FURTHER_SPECIFIED.concretizeForEncoding()
            } catch (ex: Exception) {
                throw Error("The default values are not valid", ex)
            }
        }
    }
}