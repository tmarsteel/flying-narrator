package io.github.tmarsteel.flyingnarrator

object HermiteSpline {
    private fun h00(t: Double) = 2 * t * t * t - 3 * t * t + 1
    private fun h10(t: Double) = t * t * t - 2 * t * t + t
    private fun h01(t: Double) = -2 * t * t * t + 3 * t * t
    private fun h11(t: Double) = t * t * t - t * t

    fun interpolate(a: ControlPoint, b: ControlPoint, t: Double): Vector3 {
        val distance = (b.position - a.position).length
        val scaledTA = a.tangent * distance
        val scaledTB = b.tangent * distance
        return a.position * h00(t) + scaledTA * h10(t) + b.position * h01(t) + scaledTB * h11(t)
    }

    data class ControlPoint(
        val position: Vector3,
        val tangent: Vector3,
    )
}