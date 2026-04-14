package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.RoadSegment
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.zipWithNextAndEmitLast

data class TmpSegment(
    val roadSegmentIndex: Int,
    val roadSegment: RoadSegment,
    var angleToStart: Double,
    val startsAtTrackDistance: Double,
) {
    val center = startsAtTrackDistance + roadSegment.length / 2.0

    companion object {
        fun fromRoute(route: Route): List<TmpSegment> {
            var distanceCarry = 0.0
            var angleCarry = 0.0
            val tmpSegments = route.asSequence().withIndex().zipWithNextAndEmitLast(
                zipMapper = { (segmentIdx, a), (_, b) ->
                    val s = TmpSegment(segmentIdx, a, angleCarry, distanceCarry)
                    distanceCarry += a.length
                    angleCarry += a.forward.angleTo(b.forward)
                    s
                },
                mapLast = { (vecIdx, vec) -> TmpSegment(vecIdx, vec, 0.0, distanceCarry) }
            )
                .toList()
            tmpSegments.last().angleToStart = tmpSegments[tmpSegments.lastIndex - 1].angleToStart

            return tmpSegments
        }
    }
}