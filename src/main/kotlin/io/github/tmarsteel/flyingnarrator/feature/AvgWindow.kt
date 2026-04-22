package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.unit.Angle
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.radians
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sumOf
import io.github.tmarsteel.flyingnarrator.weightedAverageOf
import io.github.tmarsteel.flyingnarrator.windowsWhere
import kotlin.math.pow
import kotlin.properties.Delegates

data class AvgWindow(
    val tmpSegments: List<TmpSegment>,
    val length: Distance,
    val angle: Angle,
) {
    var deltaAnglePerArcMeter by Delegates.notNull<Angle>()

    companion object {
        fun avgWeight(progressIntoWindow: Double) = -(2.0 * progressIntoWindow - 1.0).pow(2) + 1.0

        fun fromTmpSegments(tmpSegments: List<TmpSegment>): List<AvgWindow> {
            val avgWindows = tmpSegments
                .windowsWhere(yieldCopies = true) { w -> w.sumOf { it.roadSegment.length } > 20.0.meters }
                .map { w ->
                    val windowStartAt = w.first().startsAtTrackDistance
                    val windowLength = (w.last().let { it.startsAtTrackDistance + it.roadSegment.length } - windowStartAt)
                    val weightedAvg = w.asSequence().weightedAverageOf(
                        value = { it.angleToStart },
                        weight = {
                            val progressIntoWindow = (it.center - windowStartAt) / windowLength
                            check(progressIntoWindow in 0.0 .. 1.0)
                            avgWeight(progressIntoWindow)
                        }
                    )
                    AvgWindow(w, windowLength, weightedAvg)
                }
                .toList()

            avgWindows.zipWithNext().forEach { (a, b) ->
                b.deltaAnglePerArcMeter = ((b.angle - a.angle) / a.length.toDoubleInMeters())
            }
            avgWindows.first().deltaAnglePerArcMeter = 0.radians

            return avgWindows
        }
    }
}