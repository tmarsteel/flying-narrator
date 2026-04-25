package io.github.tmarsteel.flyingnarrator.audio.opus

import java.nio.file.Paths
import javax.sound.sampled.AudioFormat
import kotlin.io.path.inputStream

fun main() {
    convertToOgg()
}

fun convertToOgg() {
    val infile = Paths.get("pacenotes-my.ogg")
    val sampleRate = 28000.toFloat()
    val format = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sampleRate,
        16,
        1,
        2,
        sampleRate,
        false,
    )
    infile.inputStream().use { fileIn ->
        OggOpusDecodingAudioInputStream.peek(fileIn)
            .let { it as OggOpusDecodingAudioInputStream.PeekedStream.Supported }
            .toStream(format)
            .use { pcmIn ->
                println(pcmIn.format)
            }
    }
}