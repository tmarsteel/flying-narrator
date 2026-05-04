package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.mutableSignalOf
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.MLine
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.route.RoadSegment
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.awt.geom.Rectangle2D

class RouteEditorViewModel(
    route: Route,
) {
    val segments = route
        .asSequence()
        .drop(1)
        .runningFold(
            RouteSegmentModel(
                route.first(),
                MLine(Vector3.ORIGIN, route.first().forward),
                0.meters,
            )
        ) { previousSegmentModel, roadSegment ->
            RouteSegmentModel(
                roadSegment,
                MLine(previousSegmentModel.line.endPoint, roadSegment.forward),
                previousSegmentModel.startsAtDistance + roadSegment.length,
            )
        }
        .toList()

    val routeBounds: Rectangle2D.Double = computeRouteBounds(route)

    /**
     * @param searchRange limit the search to this index range; defaults to the full route.
     *   Pass a window around the current position during drag to prevent the point from jumping
     *   to distant segments when the cursor strays far from the route.
     * @return the index of the segment closest to [point], or `-1` if none is reasonably close
     */
    fun getIndexOfSegmentClosestTo(point: Vector3, searchRange: IntRange = segments.indices): Int {
        return segments
            .asSequence()
            .withIndex()
            .filter { (index, _) -> index in searchRange }
            .mapNotNull { (segmentIndex, segmentModel) ->
                val vertical = segmentModel.line.findVerticalLineThrough(point, onlyIfOnSegment = true)
                if (vertical == null && !segmentModel.line.contains2d(point)) {
                    return@mapNotNull null
                }
                val distance = vertical?.direction?.length2d ?: 0.0
                Pair(segmentIndex, distance)
            }
            .minByOrNull { it.second }
            ?.first
            ?: -1
    }

    /**
     * @return the segment that contains the given [distanceAlongTrack] along with the distance offset into it to
     * the precise location, or null of [distanceAlongTrack] is before the start or beyond the finish
     */
    fun findSegmentForDistanceAlongTrack(distanceAlongTrack: Distance): Pair<RouteSegmentModel, Distance>? {
        val idx = segments.binarySearchBy(distanceAlongTrack) { it.startsAtDistance }
        if (idx >= 0) {
            // exact hit on the start point
            return Pair(segments[idx], 0.meters)
        }

        val insertionPoint = -(idx + 1)
        if (insertionPoint == 0 || insertionPoint >= segments.size) {
            return null
        }
        val segment = segments[insertionPoint]
        return Pair(segment, distanceAlongTrack - segment.startsAtDistance)
    }

    fun makeCornerModel(corner: Feature.Corner): CornerModel = CornerModel(
        mutableSignalOf(
            IntRange(
                segments.asSequence().map { it.base }.indexOf(corner.segments.first()),
                segments.asSequence().map { it.base }.indexOf(corner.segments.last())
            )
        )
    )

    class RouteSegmentModel(
        val base: RoadSegment,
        val line: MLine,
        val startsAtDistance: Distance,
    ) {
        fun getLocationOfDistanceIntoSegment(distance: Distance): Vector3 {
            check(distance <= base.length)
            return line.startPoint + line.direction.withLength(distance.toDoubleInMeters())
        }
    }

    class CornerModel(
        val segmentIndices: MutableSignal<IntRange>
    )

    companion object {
        private fun computeRouteBounds(route: Route): Rectangle2D.Double {
            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            route.fold(Vector3.ORIGIN) { carryPt, segment ->
                val nextCarry = carryPt + segment.forward
                minX = minX.coerceAtMost(nextCarry.x)
                maxX = maxX.coerceAtLeast(nextCarry.x)
                minY = minY.coerceAtMost(nextCarry.y)
                maxY = maxY.coerceAtLeast(nextCarry.y)
                nextCarry
            }
            return Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY)
        }
    }
}