package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.Vector3
import kotlin.math.absoluteValue

val List<TrackSegment>.compoundRadius: Double
    get() {
        if (size == 1) {
            return single().radiusToNext
        }

        val totalAngle = sumOf { it.angleToNext }
        if (totalAngle.absoluteValue > 2.793) {
            /** the perpendicular-line-intersection algo only works reliably for coners considerably less than 180° */
            val cutIndex = size / 2
            val part1 = subList(0, cutIndex).compoundRadius
            val part2 = subList(cutIndex, size).compoundRadius
            if (part1 == Double.POSITIVE_INFINITY) {
                return part2
            }
            if (part2 == Double.POSITIVE_INFINITY) {
                return part1
            }
            return (part1 + part2) / 2.0
        }

        val cornerStartsAt = Vector3.Companion.ORIGIN
        val cornerStart = first().roadSegment
        val cornerEndsAt = map { it.roadSegment }.reduce { acc, segment -> acc + segment }
        val cornerEnd = last().roadSegment
        val line1 = MLine(cornerStartsAt, cornerStart.rotate2d90degCounterClockwise())
        val line2 = MLine(cornerEndsAt, cornerEnd.rotate2d90degCounterClockwise())
        val center = line1.intersect2d(line2) ?: return Double.POSITIVE_INFINITY
        return (cornerEndsAt - center).length2d
    }