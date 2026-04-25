package io.github.tmarsteel.flyingnarrator.audio.opus

import java.io.File
import java.io.InputStream
import java.net.URL
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import javax.sound.sampled.spi.AudioFileReader

class OggOpusFileReader : AudioFileReader() {
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
        val peeked = peekInternal(getInputStream, canGetMultipleStreams)
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

    private fun peekInternal(
        getInputStream: () -> InputStream,
        canGetMultipleStreams: Boolean,
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
            try {
                peeked = OggOpusDecodingAudioInputStream.peek(stream)
            } finally {
                if (marked) {
                    stream.reset()
                }
            }
        }

        return peeked
    }

    private fun getAudioInputStreamInternal(
        getInputStream: () -> InputStream,
        canGetMultipleStreams: Boolean,
        closeOnError: Boolean,
    ): OggOpusDecodingAudioInputStream {
        val peeked = peekInternal(getInputStream, canGetMultipleStreams)
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