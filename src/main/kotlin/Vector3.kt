package io.github.tmarsteel.flyingnarrator

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.withSign

private const val HALFPI = PI / 2.0

@Serializable
data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    val length2d: Double by lazy {
        sqrt(x * x + y * y)
    }

    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)

    operator fun times(scalar: Double): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)

    fun angleFromPositiveY(): Double = when {
        y == 0.0 -> (PI / 2.0).withSign(x)
        x == 0.0 -> if (y > 0) 0.0 else PI
        else -> {
            // adapted atan2 calculation
            val angleAwayFromPositiveXCCW = atan(y.absoluteValue / x.absoluteValue)
            HALFPI.withSign(x) - angleAwayFromPositiveXCCW.withSign(x.sign * y.sign)
        }
    }
    fun angleTo(bent: Vector3): Double {
        var angle = bent.angleFromPositiveY() - this.angleFromPositiveY()

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

    fun rotate2d90degClockwise(): Vector3 {
        return Vector3(y, -x, z)
    }

    fun length(): Double = sqrt(x * x + y * y + z * z)

    fun withLength2d(length: Double): Vector3 {
        return this * (length / length2d)
    }

    fun half(): Vector3 = this * 0.5

    fun coerce2dLengthAtMost(length: Double): Vector3 {
        return if (length2d > length) {
            this * (length / length2d)
        } else {
            this
        }
    }

    override fun toString() = "($x, $y, $z)"

    companion object {
        val ORIGIN = Vector3(0.0, 0.0, 0.0)
    }
}