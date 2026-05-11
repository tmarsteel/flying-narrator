package io.github.tmarsteel.flyingnarrator.route

import io.github.tmarsteel.flyingnarrator.feature.MLine
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters

/**
 * A [RouteSegmentDto], amended with information and functions for efficient and convenient processing.
 */
class RouteSegment(
    val raw: RouteSegmentDto,

    /**
     * index of this segment in [Route.segments]
     */
    val index: Int,

    /**
     * the distance from the start line where the segment starts
     */
    val startsAtDistance: Distance,

    /**
     * an [io.github.tmarsteel.flyingnarrator.feature.MLine] that represents this segment, providing access to geometric calculations and info.
     */
    val line: MLine,
) {
    val length: Distance get()= raw.forward.length.meters // this approximation is likely good enough forever
}