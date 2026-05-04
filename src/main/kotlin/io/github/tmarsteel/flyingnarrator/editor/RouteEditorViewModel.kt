package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.feature.MLine
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.route.Route
import java.awt.geom.Rectangle2D

class RouteEditorViewModel(
    val route: Route,
) {
    val mathSegments = route
        .asSequence()
        .drop(1)
        .runningFold(
            MLine(Vector3.ORIGIN, route.first().forward)
        ) { previousMathSegment, roadSegment ->
            MLine(previousMathSegment.somePoint + previousMathSegment.direction, roadSegment.forward)
        }
        .toList()

    val routeBounds: Rectangle2D.Double = computeRouteBounds(route)

    /**
     * @param searchRange limit the search to this index range; defaults to the full route.
     *   Pass a window around the current position during drag to prevent the point from jumping
     *   to distant segments when the cursor strays far from the route.
     * @return the index of the segment closest to [point], or `-1` if none is reasonably close
     */
    fun getIndexOfSegmentClosestTo(point: Vector3, searchRange: IntRange = mathSegments.indices): Int {
        return mathSegments
            .asSequence()
            .withIndex()
            .filter { (index, _) -> index in searchRange }
            .mapNotNull { (segmentIndex, segmentLine) ->
                val vertical = segmentLine.findVerticalLineThrough(point, onlyIfOnSegment = true)
                if (vertical == null && !segmentLine.contains2d(point)) {
                    return@mapNotNull null
                }
                val distance = vertical?.direction?.length2d ?: 0.0
                Pair(segmentIndex, distance)
            }
            .minByOrNull { it.second }
            ?.first
            ?: -1
    }

    companion object {
        private fun computeRouteBounds(route: Route): Rectangle2D.Double {
            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            route.fold(Vector3.ORIGIN) { carryPt, segment ->
                val nextCarry = carryPt + segment.forward
                minX = minX.coerceAtMost(nextCarry.x)
                maxX = maxX.coerceAtLeast(nextCarry.x)
                minY = minY.coerceAtMost(nextCarry.y)
                maxY = maxY.coerceAtLeast(nextCarry.y)
                nextCarry
            }
            return Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY)
        }
    }
}