package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.Vector3
import io.github.tmarsteel.flyingnarrator.unit.Angle
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.radians

class AngleAccumulator(
    initial: Vector3,
) {
    private var compareTo: Vector3 = initial
    private var previous: Vector3 = initial
    private var accumulatedBeforeLastCompareSwap = 0.radians
    private var accumulatedSinceLastCompareSwap = 0.radians

    val currentAngle: Angle
        get() = accumulatedBeforeLastCompareSwap + accumulatedSinceLastCompareSwap

    fun add(vector: Vector3) {
        val incDelta = previous.angleTo(vector)
        val absDelta = compareTo.angleTo(vector)
        if (incDelta == 0.radians) {
            return
        }

        if (incDelta.sign != absDelta.sign) {
            accumulatedBeforeLastCompareSwap = currentAngle
            accumulatedSinceLastCompareSwap = 0.radians
            compareTo = previous
            add(vector)
            return
        }

        previous = vector
        accumulatedSinceLastCompareSwap = absDelta
    }
}