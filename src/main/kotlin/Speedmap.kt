package io.github.tmarsteel.flyingnarrator

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * A recording/map of how fast a racetrack throughout the route. Used to improve prediction of where the care
 * will encounter certain features on the track so that the pacenotes/callouts can be timed better.
 */
@Serializable
class Speedmap private constructor(
    val controlPoints: Array<ControlPoint>,
) {
    constructor(controlPoints: List<ControlPoint>) : this(Unit.run {
        val asArray = controlPoints.toTypedArray()
        asArray.sortWith(compareBy(ControlPoint::distanceAlongTrack))
        asArray
    })

    init {
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

        var cpIdx = controlPoints.binarySearchBy(ControlPoint::distanceAlongTrack, distanceAlongTrack)
        if (cpIdx >= 0) {
            return controlPoints[cpIdx].atTime
        }

        cpIdx = -cpIdx - 1
        check(cpIdx > 0) // this can't happen due to the invariants and preconditions
        if (cpIdx == controlPoints.size) {
            val lastCp = controlPoints.last()
            val velocity = velocityBetweenControlPoints(controlPoints[controlPoints.lastIndex - 1], lastCp)
            val distanceAfterLastCp = distanceAlongTrack - lastCp.distanceAlongTrack
            return lastCp.atTime + (distanceAfterLastCp / velocity).seconds
        }

        val previousCp = controlPoints[cpIdx - 1]
        val nextCp = controlPoints[cpIdx]
        val velocity = velocityBetweenControlPoints(previousCp, nextCp)
        val distanceAfterPreviousCp = distanceAlongTrack - previousCp.distanceAlongTrack
        return previousCp.atTime + (distanceAfterPreviousCp / velocity).seconds
    }

    /**
     * @return the estimated position on the track in meters after time [time]
     */
    fun estimatePositionAtTime(time: Duration): Double {
        require(time >= Duration.ZERO)

        var cpIdx = controlPoints.binarySearchBy(ControlPoint::atTime, time)
        if (cpIdx >= 0) {
            return controlPoints[cpIdx].distanceAlongTrack
        }

        cpIdx = -cpIdx - 1
        check(cpIdx > 0) // this can't happen due to the invariants and preconditions
        if (cpIdx == controlPoints.size) {
            val lastCp = controlPoints.last()
            val velocity = velocityBetweenControlPoints(controlPoints[controlPoints.lastIndex - 1], lastCp)
            val timeAfterLastCp = time - lastCp.atTime
            val distanceAfterLastCp = velocity * timeAfterLastCp.toDouble(DurationUnit.SECONDS)
            return lastCp.distanceAlongTrack + distanceAfterLastCp
        }

        val previousCp = controlPoints[cpIdx - 1]
        val nextCp = controlPoints[cpIdx]
        val velocity = velocityBetweenControlPoints(previousCp, nextCp)
        val timeAfterPreviousCp = time - previousCp.atTime
        val distanceAfterPreviousCp = velocity * timeAfterPreviousCp.toDouble(DurationUnit.SECONDS)
        return previousCp.distanceAlongTrack + distanceAfterPreviousCp
    }

    private fun velocityBetweenControlPoints(cpA: ControlPoint, cpB: ControlPoint): Double {
        val distanceBetween = cpB.distanceAlongTrack - cpA.distanceAlongTrack
        val velocity = distanceBetween / (cpB.atTime - cpA.atTime).toDouble(DurationUnit.SECONDS)
        return velocity
    }

    /**
     * @return a compressed version of this [Speedmap], where a call to [estimateDurationUntilDistance] is at most
     * [timeTolerance] seconds off.
     *
     */
    fun compress(timeTolerance: Double = 0.5): Speedmap {
        val cpCopy = controlPoints.toMutableList()
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

        return Speedmap(cpCopy.toTypedArray())
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
        val atTime: Duration,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Speedmap

        if (!controlPoints.contentEquals(other.controlPoints)) return false

        return true
    }

    override fun hashCode(): Int {
        return controlPoints.contentHashCode()
    }
}