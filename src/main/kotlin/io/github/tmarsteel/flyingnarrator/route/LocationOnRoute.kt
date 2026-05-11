package io.github.tmarsteel.flyingnarrator.route

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters

/**
 * Models a precise location on the track using the [RouteSegment] that contains the location
 * and an additional [distanceAlongSegment] from the segment start point
 */
class LocationOnRoute private constructor(
    val segment: RouteSegment,
    val distanceAlongSegment: Distance,
    val point: Vector3,
) {
    constructor(segment: RouteSegment, distanceAlongSegment: Distance) : this(
        segment,
        distanceAlongSegment,
        segment.line.startPoint + segment.line.direction.withLength(distanceAlongSegment.toDoubleInMeters()),
    ) {
        check(distanceAlongSegment <= segment.length)
    }

    constructor(
        segment: RouteSegment,
        point: Vector3,
    ) : this(
        segment,
        (point - segment.line.startPoint).length.meters,
        point,
    ) {
        check(segment.line.contains2d(point))
    }

    val distanceAlongRoute: Distance get()= segment.startsAtDistance + distanceAlongSegment

    fun atSegmentStart(): LocationOnRoute = if (distanceAlongSegment == 0.meters) this else {
        LocationOnRoute(
            segment,
            0.meters,
            segment.line.startPoint,
        )
    }

    fun atSegmentEnd(): LocationOnRoute = if (distanceAlongSegment == segment.length) this else {
        LocationOnRoute(
            segment,
            segment.length,
            segment.line.endPoint,
        )
    }

    override fun toString(): String {
        return "PreciseLocation(@${segment.startsAtDistance + distanceAlongSegment}, segment#${segment.index})"
    }

    companion object {
        fun atSegmentStart(segment: RouteSegment): LocationOnRoute = LocationOnRoute(
            segment,
            0.meters,
            segment.line.startPoint,
        )

        fun atSegmentEnd(segment: RouteSegment): LocationOnRoute = LocationOnRoute(
            segment,
            segment.length,
            segment.line.endPoint,
        )
    }
}