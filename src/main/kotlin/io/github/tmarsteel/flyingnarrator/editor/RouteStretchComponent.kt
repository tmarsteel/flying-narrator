package io.github.tmarsteel.flyingnarrator.editor

import com.formdev.flatlaf.ui.FlatUIUtils
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.withTransform
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.utils.DeriveFromDelegate.Companion.deriveFrom
import io.github.tmarsteel.flyingnarrator.utils.foldInto
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Point2D
import kotlin.math.roundToInt

abstract class RouteStretchComponent(
    val viewModel: RouteEditorViewModel,
    initialSegmentIndices: IntRange,
    val displayColor: Color,
    val hoverColor: Color,
    val isEditable: Boolean,
) : RouteBoundComponent {
    var segmentIndices: IntRange = initialSegmentIndices
        set(value) {
            require(value.first >= 0)
            require(value.last < viewModel.route.size)
            field = value
        }

    private val trackPoints by deriveFrom(this::segmentIndices) { (idxs) ->
        viewModel.mathSegments
            .subList(idxs.first, idxs.last + 1)
            .map { it.somePoint }
    }

    protected val displayShape by deriveFrom(this::segmentIndices) {
        createTrackOutlineShape(trackPoints, DISPLAY_SHAPE_THICKNESS)
    }
    protected val highlightDisplayShape by deriveFrom(this::segmentIndices) {
        createTrackOutlineShape(trackPoints, HIGHLIGHT_DISPLAY_SHAPE_THICKNESS)
    }
    protected val hoverTriggerShape by deriveFrom(this::segmentIndices) {
        createTrackOutlineShape(trackPoints, HOVER_TRIGGER_SHAPE_THICKNESS)
    }

    final override fun shouldCapture(pointedTrackLocation: Vector3): Boolean {
        return hoverTriggerShape.contains(pointedTrackLocation.x, pointedTrackLocation.y)
    }

    final override var isHovered = false
    final override var routeTransform: AffineTransform = AffineTransform()
        set(value) {
            field = value
            startPointHandle?.routeTransform = value
            endPointHandle?.routeTransform = value
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

    private var startPointHandle: EndPointHandle? = null
    private var endPointHandle: EndPointHandle? = null

    final override fun onSelected(addComponent: (Component) -> Unit) {
        if (startPointHandle == null) {
            startPointHandle = object : EndPointHandle(
                segmentIndices.first,
                true,
                object : EditGovernor.Editable {
                    override fun tryMoveTo(segmentIndex: Int, atStart: Boolean): Boolean {
                        if (segmentIndex > segmentIndices.last) {
                            return false
                        }

                        // TODO: validate not moving start into the previous corner

                        segmentIndices = segmentIndex..segmentIndices.last
                        return true
                    }
                }
            ) {}
        }
        if (endPointHandle == null) {
            endPointHandle = object : EndPointHandle(
                segmentIndices.last,
                false,
                object : EditGovernor.Editable {
                    override fun tryMoveTo(segmentIndex: Int, atStart: Boolean): Boolean {
                        if (segmentIndex < segmentIndices.first) {
                            return false
                        }

                        // TODO: validate not moving end into the next corner

                        segmentIndices = segmentIndices.first..segmentIndex
                        return true
                    }
                }
            ) {}
        }
        addComponent(startPointHandle!!)
        addComponent(endPointHandle!!)
    }

    override fun onDeselected() {

    }

    private abstract inner class EndPointHandle(
        segmentIndex: Int,
        atStart: Boolean,
        editGovernor: EditGovernor,
    ) : SinglePointOnRouteComponent(
        viewModel,
        segmentIndex,
        atStart,
        editGovernor,
        routeTransform,
    ) {
        init {
            setSize(20, 20)
        }

        override fun paintComponent(g: Graphics?) {
            g as Graphics2D

            FlatUIUtils.setRenderingHints(g)
            g.translate(END_HANDLE_BORDER_STROKE.lineWidth.toDouble(), END_HANDLE_BORDER_STROKE.lineWidth.toDouble())
            g.scale(width / (END_HANDLE_SHAPE.width + END_HANDLE_BORDER_STROKE.lineWidth * 2.0), height / (END_HANDLE_SHAPE.height + END_HANDLE_BORDER_STROKE.lineWidth * 2.0))
            g.color = Color.RED
            g.fill(END_HANDLE_SHAPE)
            g.stroke = END_HANDLE_BORDER_STROKE
            g.color = Color.BLACK
            g.draw(END_HANDLE_SHAPE)
        }
    }

    companion object {
        val DISPLAY_SHAPE_THICKNESS = 5.meters
        val HIGHLIGHT_DISPLAY_SHAPE_THICKNESS = 15.meters
        val HOVER_TRIGGER_SHAPE_THICKNESS = 30.meters

        val END_HANDLE_SHAPE = Ellipse2D.Double(0.0, 0.0, 10.0, 10.0)
        val END_HANDLE_BORDER_STROKE = BasicStroke(3f)

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

private fun AffineTransform.transform(point: Vector3, dst: Point2D?): Point2D {
    return transform(Point2D.Double(point.x, point.y), dst)
}