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
        stream.mark(65536)
        val peeked = try {
            OggOpusDecodingAudioInputStream.peek(stream)
        } finally {
            stream.reset()
        }

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

    override fun getAudioFileFormat(url: URL): AudioFileFormat {
        return url.openStream().use(::getAudioFileFormat)
    }

    override fun getAudioFileFormat(file: File): AudioFileFormat {
        return file.inputStream().use(::getAudioFileFormat)
    }

    override fun getAudioInputStream(stream: InputStream): AudioInputStream {
        when (val peeked = OggOpusDecodingAudioInputStream.peek(stream)) {
            is OggOpusDecodingAudioInputStream.PeekedStream.Supported -> {
                return peeked.toStream()
            }
            is OggOpusDecodingAudioInputStream.PeekedStream.Unsupported -> {
                throw UnsupportedAudioFileException(peeked.reason)
            }
        }
    }

    override fun getAudioInputStream(url: URL): AudioInputStream {
        val urlStream = url.openStream()
        try {
            return getAudioInputStream(urlStream)
        } catch (e: Exception) {
            urlStream.close()
            throw e
        }
    }

    override fun getAudioInputStream(file: File): AudioInputStream {
        val fileStream = file.inputStream()
        try {
            return getAudioInputStream(fileStream)
        } catch (e: Exception) {
            fileStream.close()
            throw e
        }
    }
}