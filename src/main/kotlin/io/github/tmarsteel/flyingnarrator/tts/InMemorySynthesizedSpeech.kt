package io.github.tmarsteel.flyingnarrator.tts

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration

class InMemorySynthesizedSpeech(
    /**
     * bytes of the audio, complete with format headers so the bytes can be written to a file unmodified,
     * as well as fed to [AudioSystem.getAudioInputStream] with correct format detection.
     */
    val audioFileBytes: ByteArray,

    override val ssmlMakers: Map<String, Duration>,
) : SynthesizedSpeech {
    override fun openNewAudioInputStream(): AudioInputStream {
        return AudioSystem.getAudioInputStream(ByteArrayInputStream(audioFileBytes))
    }
}