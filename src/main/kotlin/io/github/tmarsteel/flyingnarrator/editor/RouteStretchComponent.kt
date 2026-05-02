package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.feature.MLine
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.ui.withTransform
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.utils.foldInto
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Shape
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import javax.swing.JComponent
import kotlin.math.roundToInt

abstract class RouteStretchComponent(
    val route: Route,
    val segmentIndices: IntRange,
    val displayColor: Color,
    val hoverColor: Color,
    val isEditable: Boolean,
) : RouteBoundComponent {
    init {
        require(segmentIndices.first >= 0)
        require(segmentIndices.last < route.size)
    }

    private val trackPoints = route.asSequence()
        .runningFold(Vector3.ORIGIN) { acc, s -> acc + s.forward }
        .drop(segmentIndices.first)
        .take(segmentIndices.last - segmentIndices.first + 1)
        .toList()

    protected val displayShape = createTrackOutlineShape(trackPoints, DISPLAY_SHAPE_THICKNESS)
    protected val highlightDisplayShape = createTrackOutlineShape(trackPoints, HIGHLIGHT_DISPLAY_SHAPE_THICKNESS)
    protected val hoverTriggerShape = createTrackOutlineShape(trackPoints, HOVER_TRIGGER_SHAPE_THICKNESS)

    final override fun tryClaimHover(pointedTrackLocation: Vector3): Boolean {
        return hoverTriggerShape.contains(pointedTrackLocation.x, pointedTrackLocation.y)
    }

    final override var isHovered = false
    final override var routeTransform: AffineTransform = AffineTransform()
        set(value) {
            field = value
            startMover?.routeTransformChanged()
            endMover?.routeTransformChanged()
        }

    final override fun paint(g: Graphics2D) {
        withTransform(g, routeTransform) {
            if (isHovered) {
                g.color = hoverColor
                g.fill(highlightDisplayShape)
            }

            g.color = displayColor
            g.fill(displayShape)
        }
    }

    final override val isSelectable = isEditable

    private var startMover: EndMoverComponent? = null
    private var endMover: EndMoverComponent? = null

    final override fun onSelected(addComponent: (Component) -> Unit) {
        if (startMover == null) {
            startMover = EndMoverComponent(segmentIndices.first, true, trackPoints.first())
        }
        if (endMover == null) {
            endMover = EndMoverComponent(segmentIndices.last, false, trackPoints.last())
        }
        addComponent(startMover!!)
        addComponent(endMover!!)
    }

    private inner class EndMoverComponent(
        val segmentIndex: Int,
        val atStart: Boolean,
        val point: Vector3,
    ) : JComponent(), MouseMotionListener {
        private lateinit var selfRouteTransform: AffineTransform

        fun routeTransformChanged() {
            val locationAsDouble = routeTransform.transform(Point2D.Double(point.x, point.y), null)
            setLocation(locationAsDouble.x.roundToInt() - width / 2, locationAsDouble.y.roundToInt() - height / 2)
            selfRouteTransform = AffineTransform().apply {
                translate(-x.toDouble(), -y.toDouble())
                concatenate(this@RouteStretchComponent.routeTransform)
            }
        }

        init {
            setSize(40, 40)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            routeTransformChanged()
            addMouseMotionListener(this)
        }

        override fun mouseDragged(e: MouseEvent) {
            val pointedLocation = selfRouteTransform.inverseTransform(e.point, null).let {
                Vector3(it.x, it.y, 0.0)
            }

            val indexOfClosestSegment = findSegmentIndexClosestTo(pointedLocation)

            TODO("visualize")
        }

        private fun findSegmentIndexClosestTo(point: Vector3): Int? {
            return route
                .asSequence()
                .runningFold(Vector3.ORIGIN) { acc, s -> acc + s.forward }
                .windowed(size = 2, step = 1, partialWindows = false)
                .mapIndexedNotNull { segmentIndex, (pointA, pointB) ->
                    val line = MLine(pointA, pointB - pointA)
                    val vertical = line.findVerticalLineThrough(point, onlyIfOnSegment = true)
                    if (vertical == null && !line.contains2d(point)) {
                        return@mapIndexedNotNull null
                    }
                    val distance = vertical?.direction?.length2d ?: 0.0
                    Pair(segmentIndex, distance)
                }
                .minByOrNull { it.second }
                ?.first
        }

        override fun mouseMoved(e: MouseEvent?) {
            // nothing to do
        }

        override fun paintComponent(g: Graphics) {
            g as Graphics2D

            val orthogonal = route[segmentIndex].forward.rotate2d90degCounterClockwise().withLength2d(10.0)
            val lineStartPoint = point + orthogonal
            val lineEndPoint = point - orthogonal
            g.stroke = BasicStroke(3f)
            g.color = Color.BLACK

            withTransform(g, selfRouteTransform) {
                g.draw(Line2D.Double(lineStartPoint.x, lineStartPoint.y, lineEndPoint.x, lineEndPoint.y))
            }
        }
    }

    companion object {
        val DISPLAY_SHAPE_THICKNESS = 5.meters
        val HIGHLIGHT_DISPLAY_SHAPE_THICKNESS = 15.meters
        val HOVER_TRIGGER_SHAPE_THICKNESS = 30.meters

        private fun createTrackOutlineShape(trackPoints: Iterable<Vector3>, thickness: Distance): Shape {
            val pointsOnRouteWithPerpendiculars = trackPoints
                .asSequence()
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

private fun AffineTransform.transform(point: Vector3, dst: Point2D): Point2D {
    return transform(Point2D.Double(point.x, point.y), dst)
}