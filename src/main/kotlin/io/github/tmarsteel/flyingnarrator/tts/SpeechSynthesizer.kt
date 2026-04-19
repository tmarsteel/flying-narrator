package io.github.tmarsteel.flyingnarrator.tts

import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLDocument
import javax.sound.sampled.AudioInputStream

interface SpeechSynthesizer {
    fun synthesize(document: SSMLDocument): AudioInputStream
}