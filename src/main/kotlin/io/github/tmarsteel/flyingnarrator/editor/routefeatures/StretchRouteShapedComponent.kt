package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.operators.bimap
import io.github.fenrur.signal.operators.map
import io.github.tmarsteel.flyingnarrator.editor.IntArrayAccumulator
import io.github.tmarsteel.flyingnarrator.editor.RouteViewModel
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.ui.withTransform
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.utils.foldInto
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Shape
import kotlin.math.roundToInt

abstract class StretchRouteShapedComponent(
    val routeViewModel: RouteViewModel,
    val stretchModel: RouteViewModel.CornerModel,
    val displayColor: Color,
    val hoverColor: Color,
    val isEditable: Boolean,
) : RouteShapedComponent() {
    private val trackPoints = stretchModel.segmentIndices.map { idxs ->
        val starts = routeViewModel.segments
            .slice(idxs)
            .map { it.line.startPoint }
        starts + listOf(routeViewModel.segments[idxs.last].line.endPoint)
    }

    protected val displayShape = trackPoints.map { pts ->
        createTrackOutlineShape(pts, DISPLAY_SHAPE_THICKNESS)
    }
    protected val hoverTriggerShape= trackPoints.map { pts ->
        createTrackOutlineShape(pts, HOVER_TRIGGER_SHAPE_THICKNESS)
    }

    final override fun shouldCapture(pointedTrackLocation: Vector3): Boolean {
        return hoverTriggerShape.value.contains(pointedTrackLocation.x, pointedTrackLocation.y)
    }

    final override fun paint(g: Graphics2D) {
        withTransform(g, parent.value!!.routeTransform.value) {
            g.color = if (hovered.value) hoverColor else displayColor
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

    private inner class EndPointHandle(isCornerEntry: Boolean) : MovableLocationOnRouteComponent(
        if (isCornerEntry) {
            stretchModel.indexOfFirstSegment.bimap(
                forward = { RouteViewModel.PreciseLocation.atSegmentStart(routeViewModel.segments[it]) },
                reverse = { it.segment.index },
            )
        } else {
            stretchModel.indexOfLastSegment.bimap(
                forward = { RouteViewModel.PreciseLocation.atSegmentEnd(routeViewModel.segments[it]) },
                reverse = { it.segment.index },
            )
        },
        object : MoveGovernor {
            override fun processPotentialMove(location: RouteViewModel.PreciseLocation): RouteViewModel.PreciseLocation? {
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
    )

    companion object {
        val DISPLAY_SHAPE_THICKNESS = 5.meters
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