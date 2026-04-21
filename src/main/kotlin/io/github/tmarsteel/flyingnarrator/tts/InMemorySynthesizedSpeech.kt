package io.github.tmarsteel.flyingnarrator.tts

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration

class InMemorySynthesizedSpeech(
    /**
     * bytes of the audio, as if read from a WAV file
     */
    val wavFileBytes: ByteArray,

    override val ssmlMakers: Map<String, Duration>,
) : SynthesizedSpeech {
    override fun openNewAudioInputStream(): AudioInputStream {
        return AudioSystem.getAudioInputStream(ByteArrayInputStream(wavFileBytes))
    }
}