package io.github.tmarsteel.flyingnarrator.narrator

import io.github.tmarsteel.flyingnarrator.zipWithNextAndEmitLast
import java.nio.file.Path
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.time.Duration
import kotlin.time.DurationUnit

fun <Key> Path.loadClips(startPositions: Map<Key, Duration>): Map<Key, Clip> {
    val (format, data) = AudioSystem.getAudioInputStream(toFile()).use { ais ->
        ais.format to ais.readAllBytes()
    }
    fun durationToByteOffset(duration: Duration): Int = (duration.toDouble(DurationUnit.SECONDS) * format.frameRate.toDouble()).toInt() * format.frameSize
    fun cropClip(startOffset: Int, endOffset: Int, key: Key): Pair<Key, Clip> {
        if (startOffset !in data.indices || endOffset !in data.indices) {
            throw IllegalArgumentException("Clip $key is out of bounds of file $this")
        }
        val clip = AudioSystem.getClip()
        clip.open(format, data, startOffset, endOffset - startOffset + 1)
        return Pair(key, clip)
    }

    return startPositions.entries.sortedBy { it.value }
        .asSequence()
        .zipWithNextAndEmitLast(
            zipMapper = { (key, startsAt), (_, nextStartsAt) ->
                val startOffset = durationToByteOffset(startsAt)
                val endOffset = durationToByteOffset(nextStartsAt) - 1
                cropClip(startOffset, endOffset, key)
            },
            mapLast = { (key, startsAt) ->
                cropClip(durationToByteOffset(startsAt), data.lastIndex, key)
            }
        )
        .toMap()
}