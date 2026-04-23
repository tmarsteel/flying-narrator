package io.github.tmarsteel.flyingnarrator.geometry

import io.github.tmarsteel.flyingnarrator.utils.zipWithNextAndEmitLast
import kotlin.math.floor

object HermiteSpline {
    private fun h00(t: Double) = 2 * t * t * t - 3 * t * t + 1
    private fun h10(t: Double) = t * t * t - 2 * t * t + t
    private fun h11(t: Double) = t * t * t - t * t

    fun interpolate(
        controlPoints: Sequence<ControlPoint>,
        targetSubstepLength: Double,
    ): Sequence<Vector3> {
        return controlPoints
            .zipWithNextAndEmitLast(
                zipMapper = { a, b ->
                    interpolate(
                        a,
                        b,
                        targetSubstepLength,
                        bInclusive = false,
                    )
                },
                mapLast = { sequenceOf(it.position) },
            )
            .flatten()
    }

    /**
     * @param aInclusive if true, the returned sequence will emit [a]s position as the first element
     * @param bInclusive if true, the returned sequence will emit [b]s position as the last element
     * @return a sequence of intermediate points between [a] and [b]
     */
    fun interpolate(
        a: ControlPoint,
        b: ControlPoint,
        targetSubstepLength: Double,
        aInclusive: Boolean = true,
        bInclusive: Boolean = true,
    ): Sequence<Vector3> {
        check(targetSubstepLength > 0.0) { "targetSubstepLength must be positive" }

        return sequence {
            if (aInclusive) {
                yield(a.position)
            }

            val distanceAToB = (b.position - a.position).length
            if (distanceAToB > targetSubstepLength) {
                val scaledTA = a.tangent.withLength(distanceAToB)
                val scaledTB = b.tangent.withLength(distanceAToB)
                val nSteps = floor(distanceAToB / targetSubstepLength).toInt() - 1
                val step = if (nSteps <= 0) 0.5 else 1.0 / (nSteps + 1).toDouble()
                var t = step
                repeat(nSteps) {
                    yield(interpolateSingle(a.position, scaledTA, b.position, scaledTB, t))
                    t += step
                }
            }

            if (bInclusive) {
                yield(b.position)
            }
        }
    }

    fun interpolateSingle(
        positionA: Vector3,
        scaledTangentA: Vector3,
        positionB: Vector3,
        scaledTangentB: Vector3,
        t: Double
    ): Vector3 {
        val h00 = h00(t)
        val h01 = 1.0 - h00
        return positionA * h00 + scaledTangentA * h10(t) + positionB * h01 + scaledTangentB * h11(t)
    }

    data class ControlPoint(
        val position: Vector3,
        val tangent: Vector3,
    )
}