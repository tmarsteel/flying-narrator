package io.github.tmarsteel.flyingnarrator.tts

import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLDocument

interface SpeechSynthesizer {
    @Throws(SpeechSynthesisException::class)
    fun synthesize(document: SSMLDocument): SynthesizedSpeech
}