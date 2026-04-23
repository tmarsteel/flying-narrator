package io.github.tmarsteel.flyingnarrator.audio

import java.io.InputStream
import java.io.SequenceInputStream
import javax.sound.sampled.AudioFormat
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

val AudioInputStream.timeLength: Duration get() = (frameLength.toDouble() / format.frameRate.toDouble()).seconds

fun List<AudioFormat>.distinctUnmatched(): MutableList<AudioFormat> {
    if (isEmpty()) {
        return mutableListOf()
    }

    val distincts = mutableListOf<AudioFormat>()
    distincts.add(first())
    for (format in drop(1)) {
        if (distincts.none { it.matches(format) }) {
            distincts.add(format)
        }
    }

    return distincts
}

fun List<AudioFormat>.findConvertibleTargetFormat(): AudioFormat? {
    val distincts = distinctUnmatched()
    if (distincts.size == 1) {
        return distincts.first()
    }

    distincts.addLast(AudioFormat(
        44100.0F,
        16,
        1,
        true,
        false,
    ))

    // prefer higher fidelity formats
    distincts.sortByDescending { it.sampleRate * it.sampleSizeInBits }

    for (targetCandidate in distincts) {
        if (distincts.all { it.matches(targetCandidate) || AudioSystem.isConversionSupported(targetCandidate, it) }) {
            return targetCandidate
        }
    }

    return null
}

fun List<AudioInputStream>.concatenate(): AudioInputStream {
    val audioFormat = this.map { it.format }.findConvertibleTargetFormat()
        ?: throw IllegalStateException("Different audio formats in the input, cannot convert to a common one")
    return AudioInputStream(
        this.asSequence()
            .map { AudioSystem.getAudioInputStream(audioFormat, it) }
            .fold(InputStream.nullInputStream(), ::SequenceInputStream),
        audioFormat,
        this.sumOf { it.frameLength },
    )
}