package io.github.tmarsteel.flyingnarrator.route

import io.github.tmarsteel.flyingnarrator.feature.MLine
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.awt.geom.Rectangle2D

/**
 * Semantically equivalent to a [RouteDto], amended with information and functions for efficient and convenient
 * processing
 */
class Route private constructor(
    val raw: RouteDto,
    val segments: List<RouteSegment>,
) {
    constructor(dto: RouteDto) : this(dto, buildSegments(dto))

    val bounds: Rectangle2D.Double = computeRouteBounds(segments)

    val start: LocationOnRoute = LocationOnRoute.atSegmentStart(segments.first())
    val finish: LocationOnRoute = findPreciseLocation(raw.distanceToFinish)
        ?: error("the finish line (at ${raw.distanceToFinish}) is out of bounds of the ${RouteSegment::class.simpleName}s")

    /**
     * @param searchRange limit the search to this index range; defaults to the full route.
     *   Pass a window around the current position during drag to prevent the point from jumping
     *   to distant segments when the cursor strays far from the route.
     * @return the [LocationOnRoute] on the route that is closest to [point], or `null` if there is no reasonable
     * choice in [searchRange].
     */
    fun findPreciseLocationClosestTo(point: Vector3, searchRange: IntRange = segments.indices): LocationOnRoute? {
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
            ?.let { LocationOnRoute(it.first, it.second) }
    }

    /**
     * @return the segment that contains the given [distanceAlongTrack] along with the distance offset into it to
     * the precise location, or null of [distanceAlongTrack] is before the start or beyond the finish
     */
    fun findPreciseLocation(distanceAlongTrack: Distance): LocationOnRoute? {
        val idx = segments.binarySearchBy(distanceAlongTrack) { it.startsAtDistance }
        if (idx >= 0) {
            // exact hit on the start point
            return LocationOnRoute(segments[idx], 0.meters)
        }

        val insertionPoint = -(idx + 1)
        if (insertionPoint == 0 || insertionPoint >= segments.size) {
            return null
        }
        val segment = segments[insertionPoint]
        return LocationOnRoute(segment, distanceAlongTrack - segment.startsAtDistance)
    }

    companion object {
        private fun buildSegments(dto: RouteDto): List<RouteSegment> {
            return dto.segments
                .asSequence()
                .drop(1)
                .runningFold(
                    RouteSegment(
                        dto.segments.first(),
                        0,
                        0.meters,
                        MLine(Vector3.ORIGIN, dto.segments.first().forward),
                    )
                ) { previousSegment, segmentDto ->
                    RouteSegment(
                        segmentDto,
                        previousSegment.index + 1,
                        previousSegment.startsAtDistance + segmentDto.forward.length.meters,
                        MLine(previousSegment.line.endPoint, segmentDto.forward),
                    )
                }
                .toList()
        }

        private fun computeRouteBounds(segments: List<RouteSegment>): Rectangle2D.Double {
            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            segments.asSequence()
                .map { it.line.startPoint }
                .let { it + sequenceOf(segments.last().line.endPoint) }
                .forEach { pointOnRoute ->
                    minX = minX.coerceAtMost(pointOnRoute.x)
                    maxX = maxX.coerceAtLeast(pointOnRoute.x)
                    minY = minY.coerceAtMost(pointOnRoute.y)
                    maxY = maxY.coerceAtLeast(pointOnRoute.y)
                }

            return Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY)
        }
    }
}