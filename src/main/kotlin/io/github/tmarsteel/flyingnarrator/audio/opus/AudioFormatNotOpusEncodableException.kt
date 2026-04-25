package io.github.tmarsteel.flyingnarrator.audio.opus

import javax.sound.sampled.AudioFormat

class AudioFormatNotOpusEncodableException(val format: AudioFormat, val reason: String) : RuntimeException(
    "The audio format $format cannot be encoded with opus: $reason"
)