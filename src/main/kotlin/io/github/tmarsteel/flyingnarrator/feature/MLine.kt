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
     * @return first the intersection point (in [Vector3.x] and [Vector3.y] with [Vector3.z] being `0`),
     *   or `null` in case the lines are parallel or identical;
     *   second: whether the intersection point is within the bounds of line-segments described by `this` and
     *   [other] between [somePoint] and [somePoint]+[direction]
     */
    fun intersect2d(other: MLine): Pair<Vector3, Boolean>? {
        val cgMinusFj = other.direction.x * this.direction.y - this.direction.x * other.direction.y
        if (cgMinusFj == 0.0) {
            // parallel or identical
            return null
        }

        val nForOther = (-this.somePoint.y * this.direction.x - other.somePoint.x * this.direction.y + this.somePoint.x * this.direction.y + this.direction.x * other.somePoint.y) / cgMinusFj
        val intersectionPoint = other.getPoint(nForOther)
        val (selfNForX, selfNForY) = getNPerDimension(intersectionPoint)
        if (!this.contains2d(intersectionPoint, selfNForX, selfNForY)) {
            return null
        }

        val nForSelf = when {
            selfNForX == null -> selfNForY!!
            selfNForY == null -> selfNForX
            else -> (selfNForX + selfNForY) / 2.0
        }

        val isInLineSegments = nForSelf in 0.0..1.0 && nForOther in 0.0..1.0

        return Pair(intersectionPoint, isInLineSegments)
    }

    /**
     * @return a point on this line, computed by [somePoint] + [n] * [direction].
     */
    fun getPoint(n: Double): Vector3 {
        return somePoint + direction * n
    }

    private fun getNPerDimension(point: Vector3): Triple<Double?, Double?, Double?> {
        val factorForX = if (this.direction.x == 0.0) null else (point.x - this.somePoint.x) / this.direction.x
        val factorForY = if (this.direction.y == 0.0) null else (point.y - this.somePoint.y) / this.direction.y
        val factorForZ = if (this.direction.z == 0.0) null else (point.z - this.somePoint.z) / this.direction.z

        return Triple(factorForX, factorForY, factorForZ)
    }

    private fun contains2d(
        point: Vector3,
        factorForX: Double?,
        factorForY: Double?,
        tolerance: Double = 0.00001,
    ): Boolean {
        val absAmountOff = if (factorForX == null) {
            (point.x - this.somePoint.x).absoluteValue
        } else if (factorForY == null) {
            (point.y - this.somePoint.y).absoluteValue
        } else {
            (factorForX - factorForY).absoluteValue
        }

        val relAmountOff = absAmountOff / direction.length2d
        return relAmountOff <= tolerance
    }

    /**
     * @return whether [point] is on `this`, disregarding [direction].
     */
    fun contains2d(point: Vector3, tolerance: Double = 0.00001): Boolean {
        val (factorForX, factorForY) = getNPerDimension(point)
        return contains2d(point, factorForX, factorForY, tolerance)
    }
}