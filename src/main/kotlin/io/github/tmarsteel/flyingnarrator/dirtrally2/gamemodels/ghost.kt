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

        private val startPositionByTrackId = mapOf<ULong, Vector3>(
            435uL to Vector3(-2003.5,-369.98,-469.01),
            437uL to Vector3(1936.99,-58.38,2222.36),
            438uL to Vector3(-2188.56,-77.97,2484.38),
            439uL to Vector3(1936.99,-58.38,2222.36),
            440uL to Vector3(-2188.56,-77.97,2484.38),
            441uL to Vector3(430.64,-1.73,1922.6),
            442uL to Vector3(513.47,-0.12,2051.67),
            443uL to Vector3(-2709.33,690.72,-558.48),
            444uL to Vector3(748.94,643.45,172.56),
            445uL to Vector3(-2709.39,690.71,-558.46),
            446uL to Vector3(748.96,643.44,172.59),
            447uL to Vector3(-1818.17,717.78,-22.9),
            448uL to Vector3(-1818.17,717.78,-22.9),
            449uL to Vector3(1821.24,101.58,279.71),
            450uL to Vector3(-2003.5,-369.98,-469.01),
            451uL to Vector3(1820.28,101.58,279.93),
            452uL to Vector3(-299.4,-127.17,-194.4),
            453uL to Vector3(-255.36,-124.27,-135.08),
            454uL to Vector3(-1156.92,135.93,-2340.93),
            455uL to Vector3(1135.91,-335.62,1272.95),
            456uL to Vector3(-1156.92,135.93,-2340.93),
            457uL to Vector3(1135.91,-335.62,1272.95),
            458uL to Vector3(810.73,-176.81,-987.6),
            459uL to Vector3(813.09,-176.6,-995.43),
            460uL to Vector3(1169.9,137.47,455.48),
            461uL to Vector3(2869.66,-303.78,-2029.49),
            462uL to Vector3(1169.9,137.47,455.48),
            463uL to Vector3(3062.51,-90.5,164.62),
            464uL to Vector3(2908.24,-114.37,89.11),
            465uL to Vector3(4310.47,1896.82,503.57),
            466uL to Vector3(885.23,1474.67,-3667.95),
            467uL to Vector3(4310.47,1896.82,503.57),
            468uL to Vector3(885.23,1474.67,-3667.95),
            469uL to Vector3(2823.93,1973.45,-1587.75),
            470uL to Vector3(2373.12,1933.98,-2281.12),
            471uL to Vector3(2869.64,-303.79,-2029.48),
            472uL to Vector3(1183.63,605.71,-2670.33),
            480uL to Vector3(-363.54,695.3,513.18),
            489uL to Vector3(-563.63,704.22,538.58),
            490uL to Vector3(1183.63,605.71,-2670.33),
            491uL to Vector3(-563.63,704.22,538.58),
            492uL to Vector3(-1075.22,649.47,-714.58),
            493uL to Vector3(-634.61,652.25,-952.49),
            494uL to Vector3(-702.59,711.48,818.13),
            495uL to Vector3(-363.34,695.29,513.17),
            496uL to Vector3(-702.38,711.61,820.25),
            497uL to Vector3(-721.89,707.78,655.54),
            498uL to Vector3(-733.03,708.13,657.86),
            505uL to Vector3(-4574.83,124.65,42.11),
            506uL to Vector3(4076.69,112.98,-187.25),
            507uL to Vector3(-4574.84,124.64,42.12),
            508uL to Vector3(4076.69,112.97,-187.26),
            509uL to Vector3(79.21,136.15,850.81),
            510uL to Vector3(87.07,135.24,876.82),
            511uL to Vector3(-1915.9,114.84,3747.19),
            512uL to Vector3(1932.68,129.49,836.9),
            513uL to Vector3(-1915.9,114.84,3747.19),
            514uL to Vector3(1932.66,129.49,836.94),
            515uL to Vector3(130.02,114.84,-3266.99),
            516uL to Vector3(130.02,114.84,-3266.99),
            517uL to Vector3(2697.42,542.8,-4327.99),
            518uL to Vector3(-2966.2,456.3,567.45),
            519uL to Vector3(2697.27,542.79,-4327.96),
            520uL to Vector3(-2966.18,456.31,567.37),
            521uL to Vector3(-1084.42,394.83,-2143.39),
            522uL to Vector3(-1072.72,395.32,-2143.52),
            523uL to Vector3(206.1,277.17,2693.34),
            524uL to Vector3(2551.58,312.54,-5099.47),
            525uL to Vector3(206.09,277.17,2693.31),
            526uL to Vector3(2551.51,312.54,-5099.43),
            527uL to Vector3(-26.6,195.73,-1744.73),
            528uL to Vector3(-106.69,192.66,-1614.54),
            566uL to Vector3(2848.14,177.31,187.6),
            567uL to Vector3(1013.41,535.69,-2329.96),
            568uL to Vector3(1727.94,-201.6,2246.01),
            569uL to Vector3(-1143.32,-195.83,2045.89),
            570uL to Vector3(-2957.65,510.4,-4411.75),
            571uL to Vector3(2859.81,530.27,2896.1),
            572uL to Vector3(-2958.09,2601.8,2579.91),
            573uL to Vector3(1016.35,2255.47,127.04),
            574uL to Vector3(1028.64,536.83,-2401.62),
            575uL to Vector3(2848.13,177.31,187.6),
            576uL to Vector3(1028.64,536.83,-2401.63),
            577uL to Vector3(-1236.95,229.84,2589.35),
            578uL to Vector3(-1233.32,228.76,2611.54),
            579uL to Vector3(-5704.45,182.88,-2003.84),
            580uL to Vector3(1014.04,535.76,-2332.96),
            581uL to Vector3(-5704.72,182.86,-2003.7),
            582uL to Vector3(-1569.12,483.77,-2981.57),
            583uL to Vector3(-1612.46,483.07,-3001.97),
            584uL to Vector3(-2685.6,-174.38,-2349.0),
            585uL to Vector3(1727.99,-201.6,2245.88),
            586uL to Vector3(-2685.63,-174.38,-2348.93),
            587uL to Vector3(-2752.93,-141.17,2521.52),
            588uL to Vector3(-2979.33,-147.99,2459.35),
            589uL to Vector3(-1645.08,-158.84,-1283.91),
            590uL to Vector3(-1143.51,-195.83,2045.95),
            591uL to Vector3(-1645.09,-158.83,-1283.81),
            592uL to Vector3(-3714.04,-157.06,-600.8),
            593uL to Vector3(-3733.45,-154.87,-568.27),
            594uL to Vector3(-4903.0,439.45,1785.62),
            595uL to Vector3(-2957.7,510.39,-4411.76),
            596uL to Vector3(-4903.19,439.46,1785.54),
            597uL to Vector3(-4895.4,574.67,-1946.17),
            598uL to Vector3(-4871.62,582.36,-2006.44),
            599uL to Vector3(-4767.29,451.67,2074.26),
            600uL to Vector3(2859.78,530.26,2896.12),
            601uL to Vector3(-4767.26,451.67,2074.26),
            602uL to Vector3(-2077.09,499.33,5269.2),
            603uL to Vector3(-1781.21,486.46,5160.06),
            604uL to Vector3(903.84,2259.37,209.89),
            605uL to Vector3(-2958.05,2601.8,2579.89),
            606uL to Vector3(903.81,2259.37,209.89),
            607uL to Vector3(-247.06,2362.81,1340.64),
            608uL to Vector3(-257.77,2365.47,1382.64),
            609uL to Vector3(3647.97,1888.76,-3228.07),
            610uL to Vector3(1016.34,2255.46,126.97),
            611uL to Vector3(3648.28,1888.73,-3228.01),
            612uL to Vector3(1292.17,2051.82,-1867.07),
            613uL to Vector3(1306.8,2050.57,-1875.5),
            614uL to Vector3(2028.18,-1622.49,4676.17),
            615uL to Vector3(-3225.5,-1624.19,1976.36),
            616uL to Vector3(2028.22,-1622.49,4676.06),
            617uL to Vector3(-3225.47,-1624.19,1976.4),
            618uL to Vector3(1988.06,-1633.38,7395.27),
            619uL to Vector3(1373.75,-1627.23,7585.64),
            620uL to Vector3(1934.23,-1618.91,4643.35),
            621uL to Vector3(-287.88,-1601.4,-3312.33),
            622uL to Vector3(1934.23,-1618.91,4643.35),
            623uL to Vector3(-287.89,-1601.41,-3312.34),
            624uL to Vector3(1198.2,-1591.52,-404.13),
            625uL to Vector3(1221.05,-1592.31,-394.8),
            626uL to Vector3(216.34,872.11,1525.8),
            627uL to Vector3(-504.13,929.14,23.47),
            628uL to Vector3(216.34,872.11,1525.74),
            629uL to Vector3(-504.13,929.14,23.34),
            630uL to Vector3(-1224.79,843.82,2767.68),
            631uL to Vector3(-1514.58,825.68,2946.79),
            632uL to Vector3(-2360.31,894.68,515.25),
            633uL to Vector3(1005.78,925.46,-4613.94),
            634uL to Vector3(-2360.31,894.67,515.36),
            635uL to Vector3(1005.79,925.45,-4613.88),
            636uL to Vector3(-917.47,835.09,-412.31),
            637uL to Vector3(-960.18,834.8,-349.42),
            657uL to Vector3(-761.79,418.75,-1149.97),
            658uL to Vector3(-1533.37,554.21,-1736.85),
            659uL to Vector3(-764.14,418.77,-1154.25),
            660uL to Vector3(-1489.6,551.59,-1653.95),
            661uL to Vector3(625.26,380.24,3380.92),
            662uL to Vector3(568.09,386.56,3341.44),
            663uL to Vector3(-974.63,600.73,1390.6),
            664uL to Vector3(-87.68,314.22,-403.37),
            665uL to Vector3(-974.65,600.73,1390.81),
            666uL to Vector3(-87.69,314.22,-403.37),
            667uL to Vector3(-1591.13,558.6,-1842.48),
            668uL to Vector3(-1599.19,558.23,-1857.22),
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