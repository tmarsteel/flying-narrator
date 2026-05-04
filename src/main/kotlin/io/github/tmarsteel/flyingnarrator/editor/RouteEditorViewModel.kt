package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.signalOf
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
                0,
                route.first(),
                MLine(Vector3.ORIGIN, route.first().forward),
                0.meters,
            )
        ) { previousSegmentModel, roadSegment ->
            RouteSegmentModel(
                previousSegmentModel.index + 1,
                roadSegment,
                MLine(previousSegmentModel.line.endPoint, roadSegment.forward),
                previousSegmentModel.startsAtDistance + roadSegment.length,
            )
        }
        .toList()

    val routeBounds: Rectangle2D.Double = computeRouteBounds(route)
    val start: Signal<PreciseLocation> = signalOf(PreciseLocation.atSegmentStart(segments.first()))
    val finish: Signal<PreciseLocation> = signalOf(PreciseLocation.atSegmentEnd(segments.last()))

    /**
     * @param searchRange limit the search to this index range; defaults to the full route.
     *   Pass a window around the current position during drag to prevent the point from jumping
     *   to distant segments when the cursor strays far from the route.
     * @return the [PreciseLocation] on the route that is closest to [point], or `null` if there is no reasonable
     * choice in [searchRange].
     */
    fun findPreciseLocationClosestTo(point: Vector3, searchRange: IntRange = segments.indices): PreciseLocation? {
        return segments
            .asSequence()
            .filterIndexed { index, _ -> index in searchRange }
            .mapNotNull { segmentModel ->
                val vertical = segmentModel.line.findVerticalLineThrough(point, onlyIfOnSegment = true)
                if (vertical == null && !segmentModel.line.contains2d(point)) {
                    return@mapNotNull null
                }
                val pointOnSegment = vertical?.startPoint ?: point
                val distanceFromSegment = vertical?.direction?.length2d ?: 0.0
                Triple(segmentModel, pointOnSegment, distanceFromSegment)
            }
            .minByOrNull { it.third }
            ?.let { PreciseLocation(it.first, it.second) }
    }

    /**
     * @return the segment that contains the given [distanceAlongTrack] along with the distance offset into it to
     * the precise location, or null of [distanceAlongTrack] is before the start or beyond the finish
     */
    fun findPreciseLocation(distanceAlongTrack: Distance): PreciseLocation? {
        val idx = segments.binarySearchBy(distanceAlongTrack) { it.startsAtDistance }
        if (idx >= 0) {
            // exact hit on the start point
            return PreciseLocation(segments[idx], 0.meters)
        }

        val insertionPoint = -(idx + 1)
        if (insertionPoint == 0 || insertionPoint >= segments.size) {
            return null
        }
        val segment = segments[insertionPoint]
        return PreciseLocation(segment, distanceAlongTrack - segment.startsAtDistance)
    }

    fun makeCornerModel(corner: Feature.Corner): CornerModel = CornerModel(
        mutableSignalOf(segments.asSequence().map { it.base }.indexOf(corner.segments.first())),
        mutableSignalOf(segments.asSequence().map { it.base }.indexOf(corner.segments.last())),
    )

    class RouteSegmentModel(
        val index: Int,
        val base: RoadSegment,
        val line: MLine,
        val startsAtDistance: Distance,
    )

    /**
     * Models a precise location on the track using the [RouteSegmentModel] that contains the location
     * and an additional [distanceAlongSegment] from the segment start point
     */
    class PreciseLocation private constructor(
        val segment: RouteSegmentModel,
        val distanceAlongSegment: Distance,
        val point: Vector3,
    ) {
        constructor(segment: RouteSegmentModel, distanceAlongSegment: Distance) : this(
            segment,
            distanceAlongSegment,
            segment.line.startPoint + segment.line.direction.withLength(distanceAlongSegment.toDoubleInMeters()),
        ) {
            check(distanceAlongSegment <= segment.base.length)
        }

        constructor(
            segment: RouteSegmentModel,
            point: Vector3,
        ) : this(
            segment,
            (point - segment.line.startPoint).length.meters,
            point,
        ) {
            check(segment.line.contains2d(point))
        }

        fun atSegmentStart(): PreciseLocation = if (distanceAlongSegment == 0.meters) this else {
            PreciseLocation(
                segment,
                0.meters,
                segment.line.startPoint,
            )
        }

        fun atSegmentEnd(): PreciseLocation = if (distanceAlongSegment == segment.base.length) this else {
            PreciseLocation(
                segment,
                segment.base.length,
                segment.line.endPoint,
            )
        }

        companion object {
            fun atSegmentStart(segment: RouteSegmentModel): PreciseLocation = PreciseLocation(
                segment,
                0.meters,
                segment.line.startPoint,
            )

            fun atSegmentEnd(segment: RouteSegmentModel): PreciseLocation = PreciseLocation(
                segment,
                segment.base.length,
                segment.line.endPoint,
            )
        }
    }

    class CornerModel(
        val indexOfFirstSegment: MutableSignal<Int>,
        val indexOfLastSegment: MutableSignal<Int>,
    ) {
        val segmentIndices: Signal<IntRange> = combine(indexOfFirstSegment, indexOfLastSegment, ::IntRange)
    }

    class ChicaneModel(
        val location: MutableSignal<PreciseLocation>,
        val entry: MutableSignal<Entry>,
    ) {
        enum class Entry {
            LEFT,
            RIGHT,
            UNSPECIFIED,
            ;
        }
    }

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