package io.github.tmarsteel.flyingnarrator.route

import io.github.tmarsteel.flyingnarrator.geometry.HermiteSpline
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.radians

fun Sequence<RouteSegmentDto>.oversample(targetSegmentLength: Double): Sequence<RouteSegmentDto> {
    return asIterable().oversample(targetSegmentLength)
}

fun Iterable<RouteSegmentDto>.oversample(targetSegmentLength: Double): Sequence<RouteSegmentDto> {
    val oversampledPointsWithOriginalSegment = sequence {
        val iterator = iterator()
        if (!iterator.hasNext()) {
            return@sequence
        }
        val firstSegment = iterator.next()
        var previousSegment = firstSegment
        var previousControlPoint = HermiteSpline.ControlPoint(Vector3.ORIGIN, firstSegment.forward)
        while (iterator.hasNext()) {
            val segment = iterator.next()
            val angleBisector = previousSegment.forward.angleBisectorWith(segment.forward)
            val tangent = if (previousSegment.forward.angleTo(angleBisector) > 0.radians) {
                angleBisector.rotate2d90degCounterClockwise()
            } else {
                angleBisector.rotate2d90degClockwise()
            }
            val thisControlPoint = HermiteSpline.ControlPoint(previousControlPoint.position + previousSegment.forward, tangent)
            yieldAll(
                HermiteSpline.interpolate(previousControlPoint, thisControlPoint, targetSegmentLength, aInclusive = true, bInclusive = !iterator.hasNext())
                .map { pos -> Pair(pos, previousSegment) }
            )

            previousSegment = segment
            previousControlPoint = thisControlPoint
        }
    }

    return oversampledPointsWithOriginalSegment
        .zipWithNext { (a, originalSegment), (b, _) ->
            originalSegment.copy(forward = b - a)
        }
}

private fun Vector3.angleBisectorWith(bent: Vector3): Vector3 {
    val halfAngle = this.angleTo(-bent) / 2.0
    val rotated = this.rotate2dCounterClockwise(-halfAngle)
    return rotated
}