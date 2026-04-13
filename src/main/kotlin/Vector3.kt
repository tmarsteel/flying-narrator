package io.github.tmarsteel.flyingnarrator

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.withSign

private const val HALFPI = PI / 2.0

@Serializable
data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    val length: Double by lazy {
        hypot(hypot(x, y), z)
    }
    val length2d: Double by lazy {
        hypot(x, y)
    }

    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)

    operator fun times(scalar: Double): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)

    operator fun unaryMinus(): Vector3 = Vector3(-x, -y, -z)

    fun clockwiseAngleFromPositiveY(): Double = when {
        y == 0.0 -> (PI / 2.0).withSign(x)
        x == 0.0 -> if (y > 0) 0.0 else PI
        else -> {
            // adapted atan2 calculation
            val angleAwayFromPositiveXCCW = atan(y.absoluteValue / x.absoluteValue)
            HALFPI.withSign(x) - angleAwayFromPositiveXCCW.withSign(x.sign * y.sign)
        }
    }

    fun angleTo(bent: Vector3): Double {
        var angle = bent.clockwiseAngleFromPositiveY() - this.clockwiseAngleFromPositiveY()

        when {
            angle > PI -> angle -= PI * 2
            angle < -PI -> angle += PI * 2
        }

        return angle
    }

    /**
     * Rotates the two-dimensional components of this vector ([x] and [y]) by 90° counter-clockwise.
     */
    fun rotate2d90degCounterClockwise(): Vector3 {
        return Vector3(-y, x, z)
    }

    fun rotate2dCounterClockwise(angle: Double): Vector3 {
        return Vector3(
            x = cos(angle) * x - sin(angle) * y,
            y = sin(angle) * x + cos(angle) * y,
            z = z,
        )
    }

    fun withLength2d(length: Double): Vector3 {
        return this * (length / length2d)
    }

    fun half(): Vector3 = this * 0.5

    override fun toString() = "($x, $y, $z)"

    companion object {
        val ORIGIN = Vector3(0.0, 0.0, 0.0)
    }
}