package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.io.asString
import io.github.tmarsteel.flyingnarrator.io.readDelimited
import io.github.tmarsteel.flyingnarrator.io.readDelimitedBigIntAssumeMax64Bits
import io.github.tmarsteel.flyingnarrator.io.readLEInt64
import io.github.tmarsteel.flyingnarrator.io.readVector3
import io.github.tmarsteel.flyingnarrator.io.skipDelimited
import io.github.tmarsteel.flyingnarrator.io.skipNBytes
import io.github.tmarsteel.flyingnarrator.io.skipUntil
import io.github.tmarsteel.flyingnarrator.route.Speedmap
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * Provides basic access into a ghostcar file, as the game downloads it from its API when you enable a ghostcar from the
 * leaderboard. The format is complex and intricate, it is only deciphered enough to get accurate [Speedmap]s.
 */
data class DR2Ghostcar(
    val metadata: Metadata,
    val carPositions: List<Pair<Duration, Vector3>>,
) {
    fun toSpeedmap(): Speedmap {
        val controlPoints = mutableListOf<Speedmap.ControlPoint>()
        var previousPosition = carPositions.first().second
        val timeOffset = carPositions.first().first
        var distance = 0.meters

        controlPoints.add(Speedmap.ControlPoint(0.meters, Duration.ZERO))
        for ((time, position) in carPositions.drop(1)) {
            val distanceDelta = (position - previousPosition).length.meters
            distance += distanceDelta
            controlPoints.add(Speedmap.ControlPoint(distance, time - timeOffset))
            previousPosition = position
        }

        return Speedmap(controlPoints)
    }

    data class Metadata(
        val raceTime: Duration?,
        val trackModelId: ULong?,
        val vehicleClassId: ULong?,
        val nationalityId: ULong?,
        val vehicleId: ULong?,
        val livery: String,
        val trackConditions: TrackConditions,
    )

    data class TrackConditions(
        val id: UInt,
    )

    class InvalidGhostcarFileException(message: String) : IOException(message)

    companion object {
        fun parseFrom(data: InputStream): DR2Ghostcar {
            val decompressedSize = data.readLEInt64().coerceAtMost(Int.MAX_VALUE.toULong()).toInt()
            if (decompressedSize > 1024 * 1024 * 2) {
                throw InvalidGhostcarFileException("Ghost file is too large")
            }
            val compressed = data.readBytes()
            val decompressed = zlibDecompress(compressed, decompressedSize)
            val reader = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN)
            val magicBytes = reader.getInt()
            if (magicBytes != 0x54534847) {
                throw InvalidGhostcarFileException("Invalid ghost file header")
            }
            val payloadSize = reader.getInt().toUInt()
            if ((decompressed.size - 8).toUInt() != payloadSize) {
                throw InvalidGhostcarFileException("Inconsistent payload size header; $payloadSize bytes claimed in payload, but ${decompressed.size} are present")
            }

            reader.skipDelimited()
            val metadata = reader.readMetadata()
            val trackModelId = metadata.trackModelId
                ?: throw InvalidGhostcarFileException("Track model ID not found, cannot read telemetry without it")
            val startPosition = startPositionByTrackId[trackModelId]
                ?: throw IOException("Unknown track model ID: $trackModelId, cannot read telemetry for unknown tracks")

            // telemetry start varies, but the start position can be clearly determined because the telemetry data
            // has an explicit size
            reader.skipUntil {
                val telemetrySize = it.getInt()
                it.position() + telemetrySize == decompressedSize
            }

            val carPositions = reader.readCarPositions(startPosition)
                .toList()

            return DR2Ghostcar(metadata, carPositions)
        }

        /**
         * TODO: fill these from the start gate positions; track_model ids are in base.ctpk
         */
        private val startPositionByTrackId = mapOf<ULong, Vector3>(
            472uL to Vector3(1185.39, 606.282, -2669.52),
        )
    }
}

private fun ByteBuffer.readMetadata(): DR2Ghostcar.Metadata {
    // all unknown parts
    skipNBytes(3)
    skipDelimited()
    skipDelimited()
    skipDelimited()
    get().let { conditionsPre ->
        if (conditionsPre != 0x08.toByte()) {
            throw DR2Ghostcar.InvalidGhostcarFileException("Byte prior to track conditions must be 0x08, got ${conditionsPre.toHexString()}")
        }
    }
    val conditions = readConditions()
    skipNBytes(4) // unknown again
    val livery = readDelimited().asString(Charsets.US_ASCII)
    var raceTime: Duration? = null
    var trackModelId: ULong? = null
    var vehicleClassId: ULong? = null
    var vehicleId: ULong? = null
    var nationalityId: ULong? = null
    repeat(14) {
        val key = get().toInt()
        when (key) {
            0x04 -> raceTime = readDelimitedBigIntAssumeMax64Bits().toLong().milliseconds
            0x05 -> trackModelId = readDelimitedBigIntAssumeMax64Bits()
            0x0A -> vehicleClassId = readDelimitedBigIntAssumeMax64Bits()
            0x0C -> nationalityId = readDelimitedBigIntAssumeMax64Bits()
            0x0F -> vehicleId = readDelimitedBigIntAssumeMax64Bits()
            else -> skipDelimited()
        }
    }

    return DR2Ghostcar.Metadata(
        raceTime,
        trackModelId,
        vehicleClassId,
        nationalityId,
        vehicleId,
        livery,
        conditions,
    )
}

private fun ByteBuffer.readConditions(): DR2Ghostcar.TrackConditions {
    val id = getInt().toUInt()
    skipDelimited() // unknown extra part
    return DR2Ghostcar.TrackConditions(id)
}

private val SAMPLING_INTERVAL = 266.67.milliseconds
private val MAX_VELOCITY = 83.0 // meters/second = 300km/h
private val MAX_POSITION_DELTA = MAX_VELOCITY * SAMPLING_INTERVAL.toDouble(DurationUnit.SECONDS)
private fun ByteBuffer.readCarPositions(startPositon: Vector3): Sequence<Pair<Duration, Vector3>> {
    skipUntil {
        val v = it.readVector3()
        !v.hasNaNComponent && (v - startPositon).length < 5.0
    }

    return sequence {
        var position = readVector3()
        var time = Duration.ZERO

        while (true) {
            yield(Pair(time, position))

            val nextPositionFound = skipUntil(
                condition = {
                    val v = it.readVector3()
                    !v.hasNaNComponent && (v - position).length <= MAX_POSITION_DELTA
                },
                onEof = {},
            )
            if (!nextPositionFound) {
                break
            }

            position = readVector3()
            time += SAMPLING_INTERVAL
        }
    }
}

private fun zlibDecompress(compressed: ByteArray, decompressedSize: Int): ByteArray {
    val decompressor = Inflater()
    decompressor.setInput(compressed)
    if (decompressor.needsInput() || decompressor.needsDictionary()) {
        throw IOException("Incomplete data, zlib inflater needs more input or dictionary")
    }
    val buffer = ByteArray(decompressedSize)
    val nDecompressed = decompressor.inflate(buffer)
    if (!decompressor.finished()) {
        throw IOException("Incomplete data, zlib inflater needs more input")
    }
    if (nDecompressed != decompressedSize) {
        throw IOException("Expected $decompressedSize bytes of decompressed data, but got $nDecompressed")
    }

    return buffer
}