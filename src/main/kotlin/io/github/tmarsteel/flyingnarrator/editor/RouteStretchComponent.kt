package io.github.tmarsteel.flyingnarrator.editor

import com.formdev.flatlaf.ui.FlatUIUtils
import io.github.fenrur.signal.operators.bimap
import io.github.fenrur.signal.operators.map
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.ui.withTransform
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.utils.foldInto
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Shape
import java.awt.geom.Ellipse2D
import kotlin.math.roundToInt

abstract class RouteStretchComponent(
    val routeViewModel: RouteEditorViewModel,
    val stretchModel: RouteEditorViewModel.CornerModel,
    val displayColor: Color,
    val hoverColor: Color,
    val isEditable: Boolean,
) : RouteBoundComponent() {
    private val trackPoints = stretchModel.segmentIndices.map { idxs ->
        val starts = routeViewModel.segments
            .slice(idxs)
            .map { it.line.startPoint }
        starts + listOf(routeViewModel.segments[idxs.last].line.endPoint)
    }

    protected val displayShape = trackPoints.map { pts ->
        createTrackOutlineShape(pts, DISPLAY_SHAPE_THICKNESS)
    }
    protected val highlightDisplayShape = trackPoints.map { pts ->
        createTrackOutlineShape(pts, HIGHLIGHT_DISPLAY_SHAPE_THICKNESS)
    }
    protected val hoverTriggerShape= trackPoints.map { pts ->
        createTrackOutlineShape(pts, HOVER_TRIGGER_SHAPE_THICKNESS)
    }

    final override fun shouldCapture(pointedTrackLocation: Vector3): Boolean {
        return hoverTriggerShape.value.contains(pointedTrackLocation.x, pointedTrackLocation.y)
    }

    final override fun paint(g: Graphics2D) {
        withTransform(g, parent.value!!.routeTransform.value) {
            if (hovered.value) {
                g.color = hoverColor
                g.fill(highlightDisplayShape.value)
            }

            g.color = displayColor
            g.fill(displayShape.value)
        }
    }

    final override val isSelectable = isEditable

    private var startPointHandle: EndPointHandle? = null
    private var endPointHandle: EndPointHandle? = null

    init {
        selected.subscribeOn(lifecycle) { isSelected ->
            if (isSelected) {
                if (startPointHandle == null) {
                    startPointHandle = EndPointHandle(true)
                }
                if (endPointHandle == null) {
                    endPointHandle = EndPointHandle(false)
                }
                parent.value!!.add(startPointHandle!!)
                parent.value!!.add(endPointHandle!!)
            } else {
                startPointHandle?.let { parent.value?.remove(it) }
                endPointHandle?.let { parent.value?.remove(it) }
            }
        }
    }

    private inner class EndPointHandle(isCornerEntry: Boolean) : PointOnTrackEditHandle(
        routeViewModel,
        if (isCornerEntry) {
            stretchModel.indexOfFirstSegment.bimap(
                forward = { RouteEditorViewModel.PreciseLocation.atSegmentStart(routeViewModel.segments[it]) },
                reverse = { it.segment.index },
            )
        } else {
            stretchModel.indexOfLastSegment.bimap(
                forward = { RouteEditorViewModel.PreciseLocation.atSegmentEnd(routeViewModel.segments[it]) },
                reverse = { it.segment.index },
            )
        },
        object : EditGovernor.Editable {
            override fun processPotentialMove(location: RouteEditorViewModel.PreciseLocation): RouteEditorViewModel.PreciseLocation? {
                if (isCornerEntry) {
                    if (location.segment.index > stretchModel.indexOfLastSegment.value) {
                        return null
                    }
                    return location.atSegmentStart()
                } else {
                    if (location.segment.index < stretchModel.indexOfFirstSegment.value) {
                        return null
                    }
                    return location.atSegmentEnd()
                }
            }
        },
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