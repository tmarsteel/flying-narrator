package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.RoadSegment
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.unit.Angle
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.degrees
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.radians
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.zipWithNextAndEmitLast

data class TmpSegment(
    val roadSegmentIndex: Int,
    val roadSegment: RoadSegment,
    var angleToStart: Angle,
    val startsAtTrackDistance: Distance,
) {
    val center = startsAtTrackDistance + roadSegment.length / 2.0

    companion object {
        fun fromRoute(route: Route): List<TmpSegment> {
            var distanceCarry = 0.meters
            var angleCarry = 0.radians
            val tmpSegments = route.asSequence().withIndex().zipWithNextAndEmitLast(
                zipMapper = { (segmentIdx, a), (_, b) ->
                    val s = TmpSegment(segmentIdx, a, angleCarry, distanceCarry)
                    distanceCarry += a.length
                    angleCarry += a.forward.angleTo(b.forward)
                    s
                },
                mapLast = { (vecIdx, vec) -> TmpSegment(vecIdx, vec, 0.degrees, distanceCarry) }
            )
                .toList()
            tmpSegments.last().angleToStart = tmpSegments[tmpSegments.lastIndex - 1].angleToStart

            return tmpSegments
        }
    }
}