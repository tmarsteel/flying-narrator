package io.github.tmarsteel.flyingnarrator.audio.opus

import java.io.File
import java.io.OutputStream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.spi.AudioFileWriter

class OggOpusFileWriter : AudioFileWriter() {
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