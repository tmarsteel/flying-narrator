package io.github.tmarsteel.flyingnarrator

import kotlin.math.PI
import kotlin.math.sqrt

data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)

    operator fun times(scalar: Double): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)

    fun angleTowardsPositiveY(): Double = Math.atan2(y, x)
    fun angleFrom(straight: Vector3): Double {
        var angle = straight.angleTowardsPositiveY() - this.angleTowardsPositiveY()

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

    /**
     * @return the length of this vector, disregarding [z]
     */
    fun length2d(): Double = sqrt(x * x + y * y)

    fun coerce2dLengthAtMost(length: Double): Vector3 {
        val selfLength2d = length2d()
        return if (selfLength2d > length) {
            this * (length / selfLength2d)
        } else {
            this
        }
    }

    companion object {
        val ORIGIN = Vector3(0.0, 0.0, 0.0)
    }
}