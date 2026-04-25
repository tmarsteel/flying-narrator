package io.github.tmarsteel.flyingnarrator.audio.opus

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.spi.FormatConversionProvider

class OpusFormatConversionProvider : FormatConversionProvider() {
    override fun getSourceEncodings(): Array<out AudioFormat.Encoding> {
        return SOURCE_ENCODINGS
    }

    override fun getTargetEncodings(): Array<out AudioFormat.Encoding> {
        return TARGET_ENCODINGS
    }

    override fun getTargetEncodings(sourceFormat: AudioFormat): Array<out AudioFormat.Encoding> {
        return when (sourceFormat.encoding) {
            AudioFormat.Encoding.PCM_SIGNED -> {
                if (OggOpusEncodingAudioInputStream.canEncode(sourceFormat)) {
                    arrayOf(OggOpusEncoding.OPUS_NOT_FURTHER_SPECIFIED)
                } else {
                    arrayOf()
                }
            }
            is OggOpusEncoding -> arrayOf(AudioFormat.Encoding.PCM_SIGNED)
            else -> emptyArray()
        }
    }

    override fun getTargetFormats(
        targetEncoding: AudioFormat.Encoding,
        sourceFormat: AudioFormat
    ): Array<out AudioFormat> {
        return when (sourceFormat.encoding) {
            AudioFormat.Encoding.PCM_SIGNED -> {
                if (OggOpusEncodingAudioInputStream.canEncode(sourceFormat)) {
                    arrayOf(OpusAudioFormat.forOpusEncodableFormat(
                        sourceFormat,
                        OggOpusEncoding.OPUS_NOT_FURTHER_SPECIFIED,
                    ))
                } else {
                    emptyArray()
                }
            }
            is OggOpusEncoding -> arrayOf(AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.sampleRate,
                16,
                sourceFormat.channels,
                16,
                sourceFormat.sampleRate,
                false,
            ))
            else -> emptyArray()
        }
    }

    override fun getAudioInputStream(
        targetEncoding: AudioFormat.Encoding,
        sourceStream: AudioInputStream
    ): AudioInputStream {
        if (sourceStream.format.encoding is OggOpusEncoding) {
            OggOpusDecodingAudioInputStream.getReasonCannotDecodeToEncoding(targetEncoding)?.let {
                throw IllegalArgumentException("Cannot decode opus to $targetEncoding: $it")
            }

            when (val peeked = OggOpusDecodingAudioInputStream.peek(sourceStream)) {
                is OggOpusDecodingAudioInputStream.PeekedStream.Supported -> {
                    return peeked.toStream()
                }
                is OggOpusDecodingAudioInputStream.PeekedStream.Unsupported -> {
                    throw IllegalArgumentException("This is not a valid audio/ogg; encoder=opus stream: ${peeked.reason}")
                }
            }
        }

        if (targetEncoding is OggOpusEncoding) {
            return OggOpusEncodingAudioInputStream(sourceStream, OpusAudioFormat.forOpusEncodableFormat(
                sourceStream.format,
                targetEncoding,
            ))
        }

        throw IllegalArgumentException("${this::class.simpleName} cannot convert to $targetEncoding")
    }

    override fun getAudioInputStream(
        targetFormat: AudioFormat,
        sourceStream: AudioInputStream
    ): AudioInputStream {
        if (sourceStream.format.encoding is OggOpusEncoding) {
            OggOpusDecodingAudioInputStream.getReasonCannotDecodeToFormat(targetFormat)?.let {
                throw IllegalArgumentException("Cannot decode opus to $targetFormat: $it")
            }

            when (val peeked = OggOpusDecodingAudioInputStream.peek(sourceStream)) {
                is OggOpusDecodingAudioInputStream.PeekedStream.Supported -> {
                    return peeked.toStream(targetFormat)
                }
                is OggOpusDecodingAudioInputStream.PeekedStream.Unsupported -> {
                    throw IllegalArgumentException("This is not a valid audio/ogg; encoder=opus stream: ${peeked.reason}")
                }
            }
        }

        if (targetFormat.encoding is OggOpusEncoding) {
           return OggOpusEncodingAudioInputStream(sourceStream, targetFormat)
        }

        throw IllegalArgumentException("${this::class.simpleName} cannot convert to $targetFormat")
    }

    companion object {
        @JvmStatic
        private val SOURCE_ENCODINGS = arrayOf(AudioFormat.Encoding.PCM_SIGNED, OggOpusEncoding.OPUS_NOT_FURTHER_SPECIFIED)

        @JvmStatic
        private val TARGET_ENCODINGS = SOURCE_ENCODINGS
    }
}