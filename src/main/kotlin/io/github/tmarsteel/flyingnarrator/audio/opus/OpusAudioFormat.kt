package io.github.tmarsteel.flyingnarrator.audio.opus

import tomp2p.opuswrapper.Opus
import javax.sound.sampled.AudioFormat

object OpusAudioFormat {
    fun forOpusEncodableFormat(
        sourceFormat: AudioFormat,
        opusEncoding: OggOpusEncoding,
    ): AudioFormat {
        OggOpusEncodingAudioInputStream.getReasonFormatCannotBeEncoded(sourceFormat)?.let {
            throw AudioFormatNotOpusEncodableException(sourceFormat, it)
        }

        return AudioFormat(
            opusEncoding,
            sourceFormat.sampleRate,
            16,
            sourceFormat.channels,
            16,
            sourceFormat.sampleRate,
            false,
        )
    }

    fun closestEncodableFormat(targetFormat: AudioFormat): AudioFormat {
        val encoding = targetFormat.encoding
            .takeIf { it is OggOpusEncoding || it == AudioFormat.Encoding.PCM_SIGNED }
            ?: AudioFormat.Encoding.PCM_SIGNED

        val sampleRate = SAMPLE_RATE_TO_BANDWIDTH_CONSTANT
            .keys
            .asSequence()
            .filter { it >= targetFormat.sampleRate }
            .firstOrNull()
            ?: SAMPLE_RATE_TO_BANDWIDTH_CONSTANT.keys.max()

        val nChannels = SUPPORTED_CHANNELS.find { it == targetFormat.channels } ?: SUPPORTED_CHANNELS.max()

        return AudioFormat(
            encoding,
            sampleRate,
            16,
            nChannels,
            2 * nChannels,
            sampleRate,
            false,
        )
    }

    @JvmStatic
    val SUPPORTED_CHANNELS = setOf(1, 2)

    @JvmStatic
    val SAMPLE_RATE_TO_BANDWIDTH_CONSTANT = mapOf(
        8000f to Opus.OPUS_BANDWIDTH_NARROWBAND,
        12000f to Opus.OPUS_BANDWIDTH_MEDIUMBAND,
        16000f to Opus.OPUS_BANDWIDTH_WIDEBAND,
        24000f to Opus.OPUS_BANDWIDTH_SUPERWIDEBAND,
        48000f to Opus.OPUS_BANDWIDTH_FULLBAND,
    )
}