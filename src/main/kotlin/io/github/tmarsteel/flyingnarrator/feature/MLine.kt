package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import kotlin.math.absoluteValue

/**
 * A line defined by a vector from origin to one point on the line
 * and a vector defining the direction of the line.
 */
internal class MLine(
    val somePoint: Vector3,
    val direction: Vector3,
) {
    /**
     * Treats this as a two-dimensional line defined by [Vector3.x] and [Vector3.y] and returns the point of intersection
     * between `this` and [other].
     * @return the intersection point (in [Vector3.x] and [Vector3.y] with [Vector3.z] being `0`),
     *         or `null` in case the lines are parallel or identical.
     */
    fun intersect2d(other: MLine): Vector3? {
        val cgMinusFj = other.direction.x * this.direction.y - this.direction.x * other.direction.y
        if (cgMinusFj == 0.0) {
            // parallel or identical
            return null
        }

        val nForOther = (-this.somePoint.y * this.direction.x - other.somePoint.x * this.direction.y + this.somePoint.x * this.direction.y + this.direction.x * other.somePoint.y) / cgMinusFj
        val intersectionPoint = other.getPoint(nForOther)
        if (!this.contains2d(intersectionPoint)) {
            return null
        }

        return intersectionPoint
    }

    /**
     * @return a point on this line, computed by [somePoint] + [n] * [direction].
     */
    fun getPoint(n: Double): Vector3 {
        return somePoint + direction * n
    }

    /**
     * @return whether [point] is on `this`, disregarding [direction].
     */
    fun contains2d(point: Vector3, tolerance: Double = 0.00001): Boolean {
        if (this.direction.x == 0.0) {
            return point.x == this.somePoint.x
        }

        if (this.direction.y == 0.0) {
            return point.y == this.somePoint.y
        }

        val factorForX = (point.x - this.somePoint.x) / this.direction.x
        val factorForY = (point.y - this.somePoint.y) / this.direction.y
        val amountOff = (factorForX - factorForY).absoluteValue / direction.length2d
        return amountOff <= tolerance
    }
}