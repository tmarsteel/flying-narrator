package io.github.tmarsteel.flyingnarrator.tts

class SpeechSynthesisInputTooLongException(message: String = "The SSML is too long", cause: Throwable? = null) : SpeechSynthesisException(message, cause)