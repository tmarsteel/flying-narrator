package io.github.tmarsteel.flyingnarrator

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.sqrt

@Serializable
data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)

    fun angleTowardsPositiveY(): Double = Math.atan2(y, x)
    fun angleFrom(straight: Vector3): Double {
        var angle = straight.angleTowardsPositiveY() - this.angleTowardsPositiveY()

        when {
            angle > PI -> angle -= PI * 2
            angle < -PI -> angle += PI * 2
        }

        return angle
    }

    fun length(): Distance {
        return Distance(sqrt(x * x + y * y + z * z))
    }

    companion object {
        val ORIGIN = Vector3(0.0, 0.0, 0.0)
    }
}