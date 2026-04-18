package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.Vector3
import kotlin.math.sign

class AngleAccumulator(
    initial: Vector3,
) {
    private var compareTo: Vector3 = initial
    private var previous: Vector3 = initial
    private var accumulatedBeforeLastCompareSwap = 0.0
    private var accumulatedSinceLastCompareSwap = 0.0

    val currentAngle: Double
        get() = accumulatedBeforeLastCompareSwap + accumulatedSinceLastCompareSwap

    fun add(vector: Vector3) {
        val incDelta = previous.angleTo(vector)
        val absDelta = compareTo.angleTo(vector)
        if (incDelta == 0.0) {
            return
        }

        if (incDelta.sign != absDelta.sign) {
            accumulatedBeforeLastCompareSwap = currentAngle
            accumulatedSinceLastCompareSwap = 0.0
            compareTo = previous
            add(vector)
            return
        }

        previous = vector
        accumulatedSinceLastCompareSwap = absDelta
    }
}