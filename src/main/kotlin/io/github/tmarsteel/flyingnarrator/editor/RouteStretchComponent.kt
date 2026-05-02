package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.utils.foldInto
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Shape
import kotlin.math.roundToInt

abstract class RouteStretchComponent(
    val route: Route,
    val segmentIndices: IntRange,
    val displayColor: Color,
    val hoverColor: Color,
) : RouteBoundComponent {
    init {
        require(segmentIndices.first >= 0)
        require(segmentIndices.last < route.size)
    }

    private val trackPoints = route.asSequence()
        .runningFold(Vector3.ORIGIN) { acc, s -> acc + s.forward }
        .drop(segmentIndices.first)
        .take(segmentIndices.last - segmentIndices.first + 1)

    private val displayShape = createTrackOutlineShape(trackPoints, DISPLAY_SHAPE_THICKNESS)
    private val hoveredDisplayShape = createTrackOutlineShape(trackPoints, HOVERED_DISPLAY_SHAPE_THICKNESS)
    private val hoerTriggerShape = createTrackOutlineShape(trackPoints, HOVER_TRIGGER_SHAPE_THICKNESS)

    final override fun tryClaimHover(pointedTrackLocation: Vector3): Boolean {
        return hoerTriggerShape.contains(pointedTrackLocation.x, pointedTrackLocation.y)
    }

    override var isHovered = false

    final override fun paint(g: Graphics2D) {
        if (isHovered) {
            g.color = hoverColor
            g.fill(hoveredDisplayShape)
        } else {
            g.color = displayColor
            g.fill(displayShape)
        }
    }

    companion object {
        val DISPLAY_SHAPE_THICKNESS = 5.meters
        val HOVERED_DISPLAY_SHAPE_THICKNESS = 15.meters
        val HOVER_TRIGGER_SHAPE_THICKNESS = 30.meters

        private fun createTrackOutlineShape(trackPoints: Sequence<Vector3>, thickness: Distance): Shape {
            val pointsOnRouteWithPerpendiculars = trackPoints
                .windowed(size = 2, step = 1, partialWindows = false)
                .filter { (p1, p2) ->
                    p1.x != p2.x || p1.y != p2.y
                }
                .withIndex()
                .map { (index, points) ->
                    val (x1, y1) = points[0]
                    val (x2, y2) = points[1]
                    val vecToP1 = Vector3(x1, y1, 0.0)
                    val vecP1P2 = Vector3(x2 - vecToP1.x, y2 - vecToP1.y, 0.0)
                    val perpendicularVec = vecP1P2.rotate2d90degCounterClockwise().withLength2d(thickness.toDoubleInMeters())
                    val endPair = Pair(Vector3(x2, y2, 0.0), perpendicularVec)
                    if (index == 0) {
                        sequenceOf(
                            Pair(vecToP1, perpendicularVec),
                            endPair
                        )
                    } else {
                        sequenceOf(endPair)
                    }
                }
                .flatten()

            val polyTopPoints = pointsOnRouteWithPerpendiculars
                .map { (vec, perpendicularVec) ->
                    vec + perpendicularVec
                }

            val polyBottomPoints = pointsOnRouteWithPerpendiculars
                .map { (vec, perpendicularVec) ->
                    vec - perpendicularVec
                }
                .toList()
                .asReversed()

            return (polyTopPoints + polyBottomPoints)
                .foldInto(Pair(IntArrayAccumulator(), IntArrayAccumulator())) { (xs, ys), p ->
                    xs.add(p.x.roundToInt())
                    ys.add(p.y.roundToInt())
                }
                .let { (xs, ys) ->
                    Polygon(xs.rawArray, ys.rawArray, xs.size)
                }
        }
    }
}