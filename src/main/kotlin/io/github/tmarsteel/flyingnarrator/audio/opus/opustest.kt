package io.github.tmarsteel.flyingnarrator.audio.opus

import java.io.ByteArrayInputStream
import java.nio.file.Paths
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.io.path.readBytes

fun main() {
    convertToOgg()
}

fun convertToOgg() {
    val infile = Paths.get("music.opus")
    val inData = infile.readBytes()
    val inStream = ByteArrayInputStream(inData)
    val outfile = infile.resolveSibling(infile.fileName.toString() + ".wav")
    AudioSystem.getAudioInputStream(inStream).use { opusIn ->
        AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, opusIn).use { resampledWaveIn ->
            AudioSystem.write(resampledWaveIn, AudioFileFormat.Type.WAVE, outfile.toFile())
        }
    }
}