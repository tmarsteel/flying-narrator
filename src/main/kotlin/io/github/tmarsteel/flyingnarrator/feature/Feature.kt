package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.RoadSegment
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.unit.Angle
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.radians
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sumOf
import io.github.tmarsteel.flyingnarrator.utils.consecutiveRuns

sealed interface Feature {
    val startsAtTrackDistance: Distance
    val length: Distance

    fun tryMergeWithSuccessor(successor: Feature): Feature?

    data class Straight(
        val segments: List<RoadSegment>,
        override val startsAtTrackDistance: Distance,
    ) : Feature {
        override val length: Distance = segments.sumOf { it.length }
        val angleFirstToLast: Angle = segments.first().forward.angleTo(segments.last().forward)

        override fun tryMergeWithSuccessor(successor: Feature): Feature? {
            if (successor !is Straight) {
                return null
            }
            return Straight(segments + successor.segments, startsAtTrackDistance)
        }
    }

    class Corner(
        val segments: List<RoadSegment>,
        override val startsAtTrackDistance: Distance,
    ) : Feature {
        val totalAngle: Angle = segments.totalAngle
        override val length: Distance by lazy { segments.sumOf { it.length } }

        val direction get() = if (totalAngle > 0.radians) Direction.RIGHT else Direction.LEFT

        override fun tryMergeWithSuccessor(successor: Feature): Feature? {
            if (successor !is Corner || successor.direction != direction) {
                return null
            }

            return Corner(segments + successor.segments, startsAtTrackDistance)
        }

        override fun toString(): String {
            return "Corner(totalAngle=$totalAngle, totalDistance=$length, direction=$direction)"
        }

        enum class Direction {
            LEFT,
            RIGHT,
            ;

            override fun toString(): String {
                return name.lowercase()
            }
        }
    }

    companion object {
        private val SEVERE_CORNER_ANGLE_DELTA_THRESHOLD = 0.0025.radians // TODO: move to config

        fun discoverIn(route: Route): List<Feature> {
            val tmpSegments = TmpSegment.fromRoute(route)
            val avgWindows = AvgWindow.fromTmpSegments(tmpSegments)
            val features = mutableListOf<Feature>()

            avgWindows
                .asSequence()
                .windowed(size = 2, step = 1)
                .consecutiveRuns { (a, b) ->
                    a.deltaAnglePerArcMeter.absoluteValue > SEVERE_CORNER_ANGLE_DELTA_THRESHOLD
                        && b.deltaAnglePerArcMeter.absoluteValue > SEVERE_CORNER_ANGLE_DELTA_THRESHOLD
                        && a.deltaAnglePerArcMeter.sign == b.deltaAnglePerArcMeter.sign
                }
                .map { (_, windowPairs) -> windowPairs.map { it.first() } }
                .map { windowsInOneCorner ->
                    val startsAt = windowsInOneCorner.first().tmpSegments.first().startsAtTrackDistance
                    val segments = route.subList(
                        windowsInOneCorner.first().tmpSegments.first().roadSegmentIndex,
                        windowsInOneCorner.last().tmpSegments.last().roadSegmentIndex + 1,
                    )
                    Corner(segments, startsAt)
                }
                .forEach { feature ->
                    features.add(feature)
                }

            return features
                //.tryMergeConsecutive(Feature::tryMergeWithSuccessor)
                .toList()
        }
    }
}

private fun extendCornersInPlace(features: MutableList<Feature>) {
    for (startIx in 0..features.size - 3) {
        extendCornersInPlaceOnSingleTransition(features.subList(startIx, startIx + 3))
    }
}

private fun extendCornersInPlaceOnSingleTransition(features: MutableList<Feature>): Boolean {
    require(features.size == 3)
    val (startCorner, straight, endCorner) = features.takeLast(3)
    if (startCorner !is Feature.Corner || straight !is Feature.Straight || endCorner !is Feature.Corner) {
        return false
    }
    val extendableSegments = listOf(startCorner.segments.last()) + straight.segments + listOf(endCorner.segments.first())
    var startExtendSegments = 0
    var startExtendDistanceCarry = 0.meters
    var endExtendSegments = 0
    var endExtendDistanceCarry = 0.meters
    var minObservedAngle: Triple<Angle, Int, Int>? = null
    while (startExtendSegments + endExtendSegments + 2 < extendableSegments.size) {
        val startCornerEndSegment = extendableSegments[startExtendSegments]
        val endCornerStartSegment = extendableSegments[extendableSegments.lastIndex - endExtendSegments]
        val cornerToCornerAngle = startCornerEndSegment.forward.angleTo(endCornerStartSegment.forward)
            .absoluteValue
        if (minObservedAngle == null || minObservedAngle.first > cornerToCornerAngle) {
            minObservedAngle = Triple(cornerToCornerAngle, startExtendSegments, endExtendSegments)
        }
        if (cornerToCornerAngle < CORNER_EXTENSION_STRAIGHTISH_ANGLE) {
            // that's as straight as we want it
            break
        }

        val startExtensionCandidate = extendableSegments[startExtendSegments + 1]
        val endExtensionCandidate = extendableSegments[extendableSegments.lastIndex - endExtendSegments - 1]
        val canExtendStart = startExtendDistanceCarry + startExtensionCandidate.length < CORNER_EXTENSION_MAX_DISTANCE
        val canExtendEnd = endExtendDistanceCarry + endExtensionCandidate.length < CORNER_EXTENSION_MAX_DISTANCE
        when {
            canExtendStart && canExtendEnd -> {
                if (startExtendDistanceCarry + startExtensionCandidate.length < endExtendDistanceCarry + endExtensionCandidate.length) {
                    startExtendSegments++
                    startExtendDistanceCarry += startExtensionCandidate.length
                } else {
                    endExtendSegments++
                    endExtendDistanceCarry += endExtensionCandidate.length
                }
            }
            canExtendStart -> {
                startExtendSegments++
                startExtendDistanceCarry += startExtensionCandidate.length
            }
            canExtendEnd -> {
                endExtendSegments++
                endExtendDistanceCarry += endExtensionCandidate.length
            }
            else -> break
        }
    }

    startExtendSegments = minObservedAngle?.second ?: 0
    endExtendSegments = minObservedAngle?.third ?: 0
    if (startExtendSegments == 0 && endExtendSegments == 0) {
        // good as-is
        return false
    }

    val newStart = if (startExtendSegments == 0) startCorner else {
        Feature.Corner(startCorner.segments + extendableSegments.subList(1, startExtendSegments + 1), startCorner.startsAtTrackDistance)
    }
    val newStraightStartsAt = straight.startsAtTrackDistance + extendableSegments.subList(0, startExtendSegments + 1).sumOf { it.length } // TODO: test whether this is correct
    val newStraight = Feature.Straight(extendableSegments.subList(startExtendSegments + 1, extendableSegments.size - endExtendSegments - 1), newStraightStartsAt)
    val newEnd = if (endExtendSegments == 0) endCorner else {
        val newEndExtraSegments = extendableSegments.subList(extendableSegments.lastIndex - endExtendSegments - 1, extendableSegments.size - 2)
        val newEndStartsAt = endCorner.startsAtTrackDistance - newEndExtraSegments.sumOf { it.length }
        Feature.Corner( newEndExtraSegments + endCorner.segments, newEndStartsAt)
    }
    check(startCorner.segments.size + straight.segments.size + endCorner.segments.size == newStart.segments.size + newStraight.segments.size + newEnd.segments.size)  {
        "buggy code :("
    }

    features[0] = newStart
    features[1] = newStraight
    features[2] = newEnd
    return true
}