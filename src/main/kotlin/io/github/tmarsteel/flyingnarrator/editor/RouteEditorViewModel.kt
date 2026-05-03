package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.feature.MLine
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.route.Route

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

    /**
     * @return the index of the segment closest to [point], or `-1` if none is reasonably close
     */
    fun getIndexOfSegmentClosestTo(point: Vector3): Int {
        return mathSegments
            .asSequence()
            .mapIndexedNotNull { segmentIndex, segmentLine ->
                val vertical = segmentLine.findVerticalLineThrough(point, onlyIfOnSegment = true)
                if (vertical == null && !segmentLine.contains2d(point)) {
                    return@mapIndexedNotNull null
                }
                val distance = vertical?.direction?.length2d ?: 0.0
                Pair(segmentIndex, distance)
            }
            .minByOrNull { it.second }
            ?.first
            ?: -1
    }
}