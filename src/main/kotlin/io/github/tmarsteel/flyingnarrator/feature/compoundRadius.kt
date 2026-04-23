package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.RoadSegment
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.unit.Angle
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.degrees
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.radians
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters

val Iterable<RoadSegment>.totalAngle: Angle
    get() {
        val iterator = iterator()
        if (!iterator.hasNext()) {
            return 0.radians
        }
        val acc = AngleAccumulator(iterator.next().forward)
        while (iterator.hasNext()) {
            acc.add(iterator.next().forward)
        }

        return acc.currentAngle
    }

val List<RoadSegment>.compoundRadius: Distance
    get() {
        check(size > 1)

        val lTotalAngle = totalAngle
        if (lTotalAngle.absoluteValue > 160.degrees) {
            /** the perpendicular-line-intersection algo only works reliably for coners considerably less than 180° */
            val cutIndex = size / 2
            val part1 = subList(0, cutIndex).compoundRadius
            val part2 = subList(cutIndex, size).compoundRadius
            if (part1 == Double.POSITIVE_INFINITY.meters) {
                return part2
            }
            if (part2 == Double.POSITIVE_INFINITY.meters) {
                return part1
            }
            return (part1 + part2) / 2.0
        }

        val cornerStartsAt = Vector3.Companion.ORIGIN
        val cornerStart = first().forward
        val cornerEndsAt = map { it.forward }.reduce { acc, segment -> acc + segment }
        val cornerEnd = last().forward
        val line1 = MLine(cornerStartsAt, cornerStart.rotate2d90degCounterClockwise())
        val line2 = MLine(cornerEndsAt, cornerEnd.rotate2d90degCounterClockwise())
        val center = line1.intersect2d(line2) ?: return Double.POSITIVE_INFINITY.meters
        return (cornerEndsAt - center).length2d.meters
    }