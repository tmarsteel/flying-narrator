package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.RepeatFirstAndLastSequence.Companion.repeatFirstAndLast
import io.github.tmarsteel.flyingnarrator.RoadSegment
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.Vector3
import io.github.tmarsteel.flyingnarrator.consecutiveRuns
import io.github.tmarsteel.flyingnarrator.windowsWhere
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.withSign

data class TrackSegment(
    val roadSegment: RoadSegment,
    val startsAtDistance: Double,
    val radiusToNext: Double,
    val angleToNext: Double,
    val arcLength: Double,
) {
    var severitySet: Boolean = false
        private set
    var severity: Double = 0.0
        get() {
            check(severitySet) { "severity not set yet" }
            return field
        }
        set(value) {
            check(!severitySet) { "severity already set" }
            severitySet = true
            field = value
        }

    companion object {
        fun fromRoute(route: Route): List<TrackSegment> {
            val segments = sequence {
                val windows = route
                    .asSequence()
                    .repeatFirstAndLast()
                    .windowed(size = 3, step = 1, partialWindows = false)

                var distanceAlongTrack = 0.0
                for ((prev, current, next) in windows) {
                    val startsAtDistance = distanceAlongTrack
                    distanceAlongTrack += current.length2d

                    var (radius, arcLength) = radiusAndArcLengthOfCorner(prev, current, next)
                    radius = radius.coerceAtMost(MAX_REPORTED_RADIUS)
                    val angle = current.angleTo(next)
                    yield(TrackSegment(current, startsAtDistance, radius, angle, arcLength))
                }
            }.toList()

            segments
                .windowsWhere { it.sumOf { it.arcLength } >= CORNER_RADIUS_AVERAGE_WINDOW_SIZE }
                .forEach { avgWindow ->
                    val windowLength = avgWindow.sumOf { it.arcLength }
                    val exactWindowCenterDistance = avgWindow.first().startsAtDistance + windowLength / 2.0
                    val windowCenterSegment = avgWindow.minBy { (it.startsAtDistance - exactWindowCenterDistance).absoluteValue }
                    if (windowCenterSegment.severitySet) {
                        return@forEach
                    }
                    val windowRadius = avgWindow.sumOf { it.radiusToNext * it.arcLength } / windowLength
                    val windowAngle = avgWindow.sumOf { it.angleToNext }
                    windowCenterSegment.severity = ((STRAIGHTISH_MIN_RADIUS - windowRadius.coerceAtMost(STRAIGHTISH_MIN_RADIUS)) / STRAIGHTISH_MIN_RADIUS)
                        .pow(3.0)
                        .withSign(windowAngle)
                }

            segments.asSequence()
                .consecutiveRuns { !it.severitySet }
                .forEach { (runStartIdx, segmentsWithoutSeverity) ->
                    val severityStart = if (runStartIdx == 0) 0.0 else segments[runStartIdx - 1].severity
                    val severityEnd = if (runStartIdx + segmentsWithoutSeverity.size == segments.size) 0.0 else segments[runStartIdx + segmentsWithoutSeverity.size].severity
                    val severityStep = (severityEnd - severityStart) / (segmentsWithoutSeverity.size + 1)
                    // TODO: change the severity proportional to the arcLength
                    var currentSeverity = severityStart + severityStep
                    for (segment in segmentsWithoutSeverity) {
                        segment.severity = currentSeverity
                        currentSeverity += severityStep
                    }
                }

            return segments
        }
    }
}

/**
 * Constructs a circle that passes through [previous] and `previous + current` with the constraint
 * that [next] is tangential to the circle at the location `previous + current`.
 * @return the radius of a curved road around [current] described by the given vectors.
 */
private fun radiusAndArcLengthOfCorner(previous: Vector3, current: Vector3, next: Vector3): Pair<Double, Double> {
    val radial1 = MLine(previous, previous.angleBisectorWith(current))
    val radial2 = MLine(previous + current, current.angleBisectorWith(next))
    val center = radial1.intersect2d(radial2) ?: return Pair(Double.POSITIVE_INFINITY, current.length2d)
    val centerToBeginOfCurrent = (previous - center)
    val centerToEndOfCurrent = ((previous + current) - center)
    val cornerRadius = centerToEndOfCurrent.length2d
    val circleSectionAngle = centerToBeginOfCurrent.angleTo(centerToEndOfCurrent).absoluteValue
    val arcLength = cornerRadius * circleSectionAngle
    return Pair(cornerRadius, arcLength)
}

private fun Vector3.angleBisectorWith(bent: Vector3): Vector3 {
    val halfAngle = this.angleTo(-bent) / 2.0
    val rotated = this.rotate2dCounterClockwise(-halfAngle)
    return rotated
}