package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.io.CompactObjectListSerializer
import io.github.tmarsteel.flyingnarrator.io.KotlinDurationAsMillisecondsSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.modules.SerializersModule
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * A recording/map of how fast a racetrack throughout the route. Used to improve prediction of where the care
 * will encounter certain features on the track so that the pacenotes/callouts can be timed better.
 */
@Serializable
class Speedmap(
    @Serializable(with = CompactObjectListSerializer::class)
    val controlPoints: List<ControlPoint>,
) {
    init {
        require(controlPoints is RandomAccess) { "the control points must be random access, otherwise the speed suffers greatly" }
        require(controlPoints.size >= 2) { "speedmap must have at least two control points" }
        require(controlPoints.first().distanceAlongTrack == 0.0) { "first control point must have distanceAlongTrack == 0.0" }
        check(
            controlPoints.asSequence().zipWithNext()
                .all { (cpA, cpB) ->
                    cpA.distanceAlongTrack < cpB.distanceAlongTrack
                        &&
                        cpA.atTime <= cpB.atTime
                }
        ) {
            "control points must be sorted by ${ControlPoint::distanceAlongTrack.name} and ${ControlPoint::atTime.name} ascending"
        }
    }

    /**
     * @return the estimated time it takes to go from the start of the track/race to the given [distanceAlongTrack]
     */
    fun estimateDurationUntilDistance(distanceAlongTrack: Double): Duration {
        require(distanceAlongTrack >= 0.0)

        return when (val location = getLocationBy(distanceAlongTrack, ControlPoint::distanceAlongTrack)) {
            is TrackLocation.AtControlPoint -> location.controlPoint.atTime
            is TrackLocation.BetweenControlPoints -> {
                val velocity = velocityBetweenControlPoints(location.previous, location.next)
                val distanceAfterPreviousCp = distanceAlongTrack - location.previous.distanceAlongTrack
                return location.previous.atTime + (distanceAfterPreviousCp / velocity).seconds
            }
            is TrackLocation.AtOrAfterLastControlPoint -> {
                val lastCp = controlPoints.last()
                val velocity = velocityBetweenControlPoints(controlPoints[controlPoints.lastIndex - 1], lastCp)
                val distanceAfterLastCp = distanceAlongTrack - lastCp.distanceAlongTrack
                return lastCp.atTime + (distanceAfterLastCp / velocity).seconds
            }
        }
    }

    /**
     * @return the estimated position on the track in meters after time [time]
     */
    fun estimatePositionAtTime(time: Duration): Double {
        require(time >= Duration.ZERO)

        return when(val location = getLocationBy(time, ControlPoint::atTime)) {
            is TrackLocation.AtControlPoint -> location.controlPoint.distanceAlongTrack
            is TrackLocation.BetweenControlPoints -> {
                val velocity = velocityBetweenControlPoints(location.previous, location.next)
                val timeAfterPreviousCp = time - location.previous.atTime
                val distanceAfterPreviousCp = velocity * timeAfterPreviousCp.toDouble(DurationUnit.SECONDS)
                return location.previous.distanceAlongTrack + distanceAfterPreviousCp
            }
            is TrackLocation.AtOrAfterLastControlPoint -> {
                val lastCp = controlPoints.last()
                val velocity = velocityBetweenControlPoints(controlPoints[controlPoints.lastIndex - 1], lastCp)
                val timeAfterLastCp = time - lastCp.atTime
                val distanceAfterLastCp = velocity * timeAfterLastCp.toDouble(DurationUnit.SECONDS)
                return lastCp.distanceAlongTrack + distanceAfterLastCp
            }
        }
    }

    fun estimateVelocityAtDistance(distanceAlongTrack: Double): Double {
        require(distanceAlongTrack >= 0.0)

        return when (val location = getLocationBy(distanceAlongTrack, ControlPoint::distanceAlongTrack)) {
            is TrackLocation.AtControlPoint -> {
                val cp = location.controlPoint
                return if (location.index > 0) {
                    val velocityBefore = velocityBetweenControlPoints(controlPoints[location.index - 1], cp)
                    val velocityAfter = velocityBetweenControlPoints(controlPoints[location.index + 1], cp)
                    (velocityAfter + velocityBefore) / 2.0
                } else {
                    velocityBetweenControlPoints(location.controlPoint, controlPoints[location.index + 1])
                }
            }
            is TrackLocation.BetweenControlPoints -> velocityBetweenControlPoints(location.previous, location.next)
            is TrackLocation.AtOrAfterLastControlPoint -> velocityBetweenControlPoints(controlPoints[controlPoints.lastIndex - 1], controlPoints.last())
        }
    }

    private fun velocityBetweenControlPoints(cpA: ControlPoint, cpB: ControlPoint): Double {
        val distanceBetween = cpB.distanceAlongTrack - cpA.distanceAlongTrack
        val velocity = distanceBetween / (cpB.atTime - cpA.atTime).toDouble(DurationUnit.SECONDS)
        return velocity
    }
    
    private inline fun <T : Comparable<T>> getLocationBy(key: T, crossinline selector: (ControlPoint) -> T): TrackLocation {
        var cpIdx = controlPoints.binarySearchBy(key, selector = selector)
        if (cpIdx >= 0) {
            return if (cpIdx == controlPoints.lastIndex) {
                TrackLocation.AtOrAfterLastControlPoint
            } else {
                TrackLocation.AtControlPoint(controlPoints[cpIdx], cpIdx)
            }
        }

        cpIdx = -cpIdx - 1
        check(cpIdx > 0) // this can't happen due to the invariants and preconditions
        if (cpIdx == controlPoints.size) {
            return TrackLocation.AtOrAfterLastControlPoint
        }

        val previousCp = controlPoints[cpIdx - 1]
        val nextCp = controlPoints[cpIdx]
        return TrackLocation.BetweenControlPoints(previousCp, nextCp)
    }

    private sealed interface TrackLocation {
        class AtControlPoint(val controlPoint: ControlPoint, val index: Int) : TrackLocation
        class BetweenControlPoints(val previous: ControlPoint, val next: ControlPoint) : TrackLocation
        object AtOrAfterLastControlPoint : TrackLocation
    }
    
    /**
     * @return a compressed version of this [Speedmap], where a call to [estimateDurationUntilDistance] is at most
     * [timeTolerance] seconds off.
     *
     */
    fun compress(timeTolerance: Double = 0.5): Speedmap {
        val cpCopy = ArrayList(controlPoints)
        var compressIndexStart = 1
        while (compressIndexStart < cpCopy.size - 1) {
            val firstCp = cpCopy[compressIndexStart]
            var compressIndexEnd = compressIndexStart + 1
            findEnd@while (compressIndexEnd < cpCopy.size) {
                val lastCp = cpCopy[compressIndexEnd]
                val preLastCp = cpCopy[compressIndexEnd - 1]
                val trueVelocity = velocityBetweenControlPoints(preLastCp, lastCp)
                val approxVelocity = velocityBetweenControlPoints(firstCp, lastCp)
                val subDistance = lastCp.distanceAlongTrack - preLastCp.distanceAlongTrack
                val approxDistance = lastCp.distanceAlongTrack - firstCp.distanceAlongTrack
                val trueTime = (subDistance / trueVelocity).seconds
                val estimatedTime = (approxDistance / approxVelocity).seconds
                val delta = estimatedTime - trueTime
                if (delta.absoluteValue > timeTolerance.seconds) {
                    compressIndexEnd--
                    break@findEnd
                }
                compressIndexEnd++
            }

            if (compressIndexEnd >= cpCopy.size) {
                compressIndexEnd = cpCopy.lastIndex
            }

            if (compressIndexEnd > compressIndexStart) {
                repeat(compressIndexEnd - compressIndexStart) {
                    cpCopy.removeAt(compressIndexStart + 1)
                }
                compressIndexStart += 2
            } else {
                compressIndexStart += 1
            }
        }

        return Speedmap(cpCopy)
    }

    @Serializable
    data class ControlPoint(
        /**
         * distance since start, in meters
         */
        val distanceAlongTrack: Double,

        /**
         * accumulative time when reaching [distanceAlongTrack]
         */
        @Serializable(with = KotlinDurationAsMillisecondsSerializer::class)
        val atTime: Duration,
    )

    companion object {
        val JSON_FORMAT = Json {
            serializersModule = SerializersModule {
                include(CompactObjectListSerializer.MODULE)
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun fromFile(file: Path): Speedmap {
            return file.inputStream().use { inStream ->
                JSON_FORMAT.decodeFromStream(inStream)
            }
        }
    }
}