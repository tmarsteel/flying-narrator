package io.github.tmarsteel.flyingnarrator.tts

import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLDocument

interface SpeechSynthesizer {
    fun synthesize(document: SSMLDocument): SynthesizedSpeech
}