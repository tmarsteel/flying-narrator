package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.mergeConsecutiveIf
import kotlin.math.absoluteValue

sealed interface Feature {
    val startsAtTrackDistance: Double
    val length: Double

    fun shouldMergeWithSuccessor(successor: Feature): Boolean
    fun mergeWithSuccessor(successor: Feature): Feature

    data class Straight(
        val segments: List<TrackSegment>,
    ) : Feature {
        override val startsAtTrackDistance: Double = segments.first().startsAtDistance
        override val length: Double = segments.sumOf { it.arcLength }
        val angleFirstToLast: Double = segments.first().roadSegment.angleTo(segments.last().roadSegment)

        override fun shouldMergeWithSuccessor(successor: Feature): Boolean {
            return successor is Straight
        }

        override fun mergeWithSuccessor(successor: Feature): Feature {
            require(shouldMergeWithSuccessor(successor))
            return Straight(segments + (successor as Straight).segments)
        }
    }

    class Corner(
        val segments: List<TrackSegment>,
    ) : Feature {
        override val startsAtTrackDistance: Double = segments.first().startsAtDistance
        val totalAngle: Double = segments.sumOf { it.angleToNext }
        override val length: Double by lazy { segments.sumOf { it.arcLength } }

        val direction get() = if (totalAngle > 0) Direction.RIGHT else Direction.LEFT

        override fun shouldMergeWithSuccessor(successor: Feature): Boolean {
            return successor is Corner && successor.direction == direction
        }

        override fun mergeWithSuccessor(successor: Feature): Feature {
            require(shouldMergeWithSuccessor(successor))
            return Corner(segments + (successor as Corner).segments)
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
        fun discoverIn(route: Route): List<Feature> {
            val segments = TrackSegment.fromRoute(route)
            val buffer = ArrayDeque<TrackSegment>()
            val features = mutableListOf<Feature>()
            var state: FeatureDetectionState = FeatureDetectionState.Straight
            for (segment in segments) {
                buffer.add(segment)
                state = state.process(buffer, features)
            }

            if (buffer.isNotEmpty()) {
                state.finish(buffer, features)
            }

            val mergedFeatures = features
                .mergeConsecutiveIf(
                    { a, b -> a.shouldMergeWithSuccessor(b) },
                    { a, b -> a.mergeWithSuccessor(b) },
                )
                .toMutableList()

            extendCornersInPlace(mergedFeatures)

            return mergedFeatures
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
    var startExtendDistanceCarry = 0.0
    var endExtendSegments = 0
    var endExtendDistanceCarry = 0.0
    var minObservedAngle: Triple<Double, Int, Int>? = null
    while (startExtendSegments + endExtendSegments < extendableSegments.size) {
        val startCornerEndSegment = extendableSegments[startExtendSegments]
        val endCornerStartSegment = extendableSegments[extendableSegments.lastIndex - endExtendSegments]
        val cornerToCornerAngle = startCornerEndSegment.roadSegment.angleTo(endCornerStartSegment.roadSegment)
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
        val canExtendStart = startExtendDistanceCarry + startExtensionCandidate.arcLength < CORNER_EXTENSION_MAX_DISTANCE
        val canExtendEnd = endExtendDistanceCarry + endExtensionCandidate.arcLength < CORNER_EXTENSION_MAX_DISTANCE
        when {
            canExtendStart && canExtendEnd -> {
                if (startExtendDistanceCarry + startExtensionCandidate.arcLength < endExtendDistanceCarry + endExtensionCandidate.arcLength) {
                    startExtendSegments++
                    startExtendDistanceCarry += startExtensionCandidate.arcLength
                } else {
                    endExtendSegments++
                    endExtendDistanceCarry += endExtensionCandidate.arcLength
                }
            }
            canExtendStart -> {
                startExtendSegments++
                startExtendDistanceCarry += startExtensionCandidate.arcLength
            }
            canExtendEnd -> {
                endExtendSegments++
                endExtendDistanceCarry += endExtensionCandidate.arcLength
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
        Feature.Corner(startCorner.segments + extendableSegments.subList(1, startExtendSegments + 1))
    }
    val newStraight = Feature.Straight(extendableSegments.subList(startExtendSegments + 1, extendableSegments.size - endExtendSegments - 1))
    val newEnd = if (endExtendSegments == 0) endCorner else {
        Feature.Corner(extendableSegments.subList(extendableSegments.lastIndex - endExtendSegments - 1, extendableSegments.size - 2) + endCorner.segments)
    }
    check(startCorner.segments.size + straight.segments.size + endCorner.segments.size == newStart.segments.size + newStraight.segments.size + newEnd.segments.size)  {
        "buggy code :("
    }

    features[0] = newStart
    features[1] = newStraight
    features[2] = newEnd
    return true
}