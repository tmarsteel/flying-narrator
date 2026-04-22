package io.github.tmarsteel.flyingnarrator.audio

import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private fun AudioInputStream.bytesForDuration(duration: Duration): Int {
    return format.frameSize * (format.frameRate.toDouble() * duration.toDouble(DurationUnit.SECONDS)).roundToInt()
}

fun AudioInputStream.skip(time: Duration) {
    skip(bytesForDuration(time).toLong())
}

fun AudioInputStream.readIntoClip(duration: Duration): Clip {
    val clip = AudioSystem.getClip()
    val bytes = ByteArray(bytesForDuration(duration))
    val actualByteSize = read(bytes)
    clip.open(format, bytes, 0, actualByteSize)
    return clip
}

val Clip.length: Duration get()= (frameLength.toDouble() / format.frameRate.toDouble()).seconds
val Clip.remaining: Duration get()= ((frameLength - longFramePosition).toDouble() / format.frameRate.toDouble()).seconds