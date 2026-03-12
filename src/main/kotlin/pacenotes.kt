package io.github.tmarsteel.flyingnarrator

fun Route.derivePacenotes(
    maxCornerRoundingDistance: Double = 20.0,
) {
    this
        .asSequence()
        .windowed(size = 2, step =1, partialWindows = false)
        .map { (a, b) ->
            val aCapped = a.coerce2dLengthAtMost(maxCornerRoundingDistance)
            val bCapped = b.coerce2dLengthAtMost(maxCornerRoundingDistance)
            TrackSegment(a, radiusOfCorner(aCapped, bCapped))
        }
        .forEach { segment ->
            println(segment.radiusToNext)
        }
}

private data class TrackSegment(
    val routeVector3: Vector3,
    val radiusToNext: Double,
)

/**
 * @return the radius of a circle that passes through the points [Vector3.ORIGIN], [base] and `base + turn`. Returns
 * [Double.POSITIVE_INFINITY] if the angle between [base] and [turn] is `0`.
 */
private fun radiusOfCorner(base: Vector3, turn: Vector3): Double {
    val line1 = MLine(Vector3.ORIGIN, base.rotate2d90degCounterClockwise())
    val line2 = MLine(base + turn, turn.rotate2d90degCounterClockwise())

    val center = line1.intersect2d(line2)
        ?: return Double.POSITIVE_INFINITY

    return center.length2d()
}

/**
 * A line defined by a vector from origin to one point on the line
 * and a vector defining the direction of the line.
 */
private class MLine(
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
    fun contains2d(point: Vector3): Boolean {
        if (this.direction.x == 0.0) {
            return point.x == this.somePoint.x
        }

        if (this.direction.y == 0.0) {
            return point.y == this.somePoint.y
        }

        val factorForX = (point.x - this.somePoint.x) / this.direction.x
        val factorForY = (point.y - this.somePoint.y) / this.direction.y
        return factorForX == factorForY
    }
}

