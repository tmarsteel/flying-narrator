package io.github.tmarsteel.flyingnarrator.audio.opus

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import javax.sound.sampled.spi.AudioFileReader
import javax.sound.sampled.spi.AudioFileWriter
import javax.sound.sampled.spi.FormatConversionProvider

object OggOpusAudioServiceProvider {
    class FileReader : AudioFileReader() {
        override fun getAudioFileFormat(stream: InputStream): AudioFileFormat {
            return getAudioFileFormatInternal({ stream }, false)
        }

        override fun getAudioFileFormat(url: URL): AudioFileFormat {
            return getAudioFileFormatInternal(url::openStream, true)
        }

        override fun getAudioFileFormat(file: File): AudioFileFormat {
            return getAudioFileFormatInternal(file::inputStream, true)
        }

        private fun getAudioFileFormatInternal(
            getInputStream: () -> InputStream,
            canGetMultipleStreams: Boolean,
        ): AudioFileFormat {
            val peeked = peekInternal(getInputStream, canGetMultipleStreams, resetOnSuccess = true)
            peeked.close()
            when (peeked) {
                is OggOpusDecodingAudioInputStream.PeekedStream.Unsupported -> throw UnsupportedAudioFileException(peeked.reason)
                is OggOpusDecodingAudioInputStream.PeekedStream.Supported -> {
                    return AudioFileFormat(
                        OggOpusAudioFileType,
                        peeked.sourceFormat,
                        AudioSystem.NOT_SPECIFIED,
                    )
                }
            }
        }



        override fun getAudioInputStream(stream: InputStream): AudioInputStream {
            return getAudioInputStreamInternal({ stream }, canGetMultipleStreams = false, closeOnError = false)
        }

        override fun getAudioInputStream(url: URL): AudioInputStream {
            return getAudioInputStreamInternal(url::openStream, canGetMultipleStreams = true, closeOnError = true)
        }

        override fun getAudioInputStream(file: File): AudioInputStream {
            return getAudioInputStreamInternal(file::inputStream, canGetMultipleStreams = true, closeOnError = true)
        }
    }

    class FileWriter : AudioFileWriter() {
        override fun getAudioFileTypes(): Array<out AudioFileFormat.Type> {
            return TYPES
        }

        override fun getAudioFileTypes(stream: AudioInputStream): Array<out AudioFileFormat.Type> {
            if (stream.format.encoding is OggOpusEncoding || OggOpusEncodingAudioInputStream.canEncode(stream.format)) {
                return TYPES
            }

            return emptyArray()
        }

        override fun write(
            stream: AudioInputStream,
            fileType: AudioFileFormat.Type,
            out: OutputStream
        ): Int {
            if (fileType !is OggOpusAudioFileType) {
                throw IllegalArgumentException("Unsupported file type: $fileType")
            }

            if (stream.format.encoding is OggOpusEncoding) {
                return StrictMath.toIntExact(stream.copyTo(out))
            }

            OggOpusEncodingAudioInputStream(
                stream,
                OpusAudioFormat.forOpusEncodableFormat(stream.format, OggOpusEncoding.OPUS_NOT_FURTHER_SPECIFIED),
                alsoCloseBase = false
            ).use { encodedStream ->
                return write(encodedStream, fileType, out)
            }
        }

        override fun write(
            stream: AudioInputStream,
            fileType: AudioFileFormat.Type,
            out: File
        ): Int {
            out.outputStream().use {
                return write(stream, fileType, it)
            }
        }

        companion object {
            @JvmStatic
            private val TYPES = arrayOf(OggOpusAudioFileType)
        }
    }

    class Converter : FormatConversionProvider() {
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

    private fun peekInternal(
        getInputStream: () -> InputStream,
        canGetMultipleStreams: Boolean,
        resetOnSuccess: Boolean,
    ): OggOpusDecodingAudioInputStream.PeekedStream {
        val peeked: OggOpusDecodingAudioInputStream.PeekedStream
        if (canGetMultipleStreams) {
            peeked = OggOpusDecodingAudioInputStream.peek(getInputStream)
        } else {
            val stream = getInputStream()
            var marked = false
            if (stream.markSupported()) {
                stream.mark(65536)
                marked = true
            }
            peeked = OggOpusDecodingAudioInputStream.peek(stream)
            if (marked && (peeked !is OggOpusDecodingAudioInputStream.PeekedStream.Supported || resetOnSuccess)) {
                stream.reset()
            }
        }

        return peeked
    }

    private fun getAudioInputStreamInternal(
        getInputStream: () -> InputStream,
        canGetMultipleStreams: Boolean,
        closeOnError: Boolean,
    ): OggOpusDecodingAudioInputStream {
        val peeked = peekInternal(getInputStream, canGetMultipleStreams, resetOnSuccess = false)
        when (peeked) {
            is OggOpusDecodingAudioInputStream.PeekedStream.Unsupported -> {
                val formatEx = UnsupportedAudioFileException(peeked.reason)
                if (closeOnError) {
                    try {
                        peeked.close()
                    } catch (closeEx: Exception) {
                        formatEx.addSuppressed(closeEx)
                    }
                }

                throw formatEx
            }
            is OggOpusDecodingAudioInputStream.PeekedStream.Supported -> {
                return try {
                    peeked.toStream()
                } catch (toStreamEx: Exception) {
                    if (closeOnError) {
                        try {
                            peeked.close()
                        } catch (closeEx: Exception) {
                            toStreamEx.addSuppressed(closeEx)
                        }
                    }

                    throw toStreamEx
                }
            }
        }
    }
}