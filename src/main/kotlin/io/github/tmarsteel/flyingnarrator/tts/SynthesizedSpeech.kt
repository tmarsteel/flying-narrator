package io.github.tmarsteel.flyingnarrator.tts

import javax.sound.sampled.AudioInputStream
import kotlin.time.Duration

interface SynthesizedSpeech {
    fun openNewAudioInputStream(): AudioInputStream
    val ssmlMakers: Map<String, Duration>
}