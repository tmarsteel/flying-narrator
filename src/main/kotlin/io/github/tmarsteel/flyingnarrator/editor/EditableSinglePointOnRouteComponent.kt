package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.operators.flatMap
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.feature.OPTIMAL_ROAD_SEGMENT_LENGTH
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.CustomCursor
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import java.awt.Cursor
import java.awt.Point
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.geom.AffineTransform
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Represents a single point on the route. It assumes that it is a child of [RouteComponent] and will always position
 * itself so that the center of the component is at the track location determined from [segmentIndex] and [atStart].
 */
abstract class EditableSinglePointOnRouteComponent(
    val viewModel: RouteEditorViewModel,
    initialSegmentIndex: Int,
    val atStart: Boolean,
    val editGovernor: EditGovernor = EditGovernor.NotEditable,
) : ReactiveRouteComponentChild(), MouseListener, MouseMotionListener {
    private val segmentIndex = mutableSignalOf(initialSegmentIndex)

    private val routePoint = segmentIndex.map { idx ->
        viewModel.segments[idx]
            .let { if (atStart) it.line.startPoint else it.line.endPoint }
            .toPoint2D()
    }

    private val resizeEvents = mutableSignalOf(Unit)
    private val selfRouteTransform: Signal<AffineTransform>
    init {
        val targetSelfLocation = combine(
            routePoint,
            parentRouteComponent.flatMap { pc -> pc?.routeTransform ?: signalOf(AffineTransform()) },
            resizeEvents,
        ) { routePoint, routeTransform, _ ->
            val locationAsDouble = routeTransform.transform(routePoint, null)
            Pair(
                Point(locationAsDouble.x.roundToInt() - width / 2, locationAsDouble.y.roundToInt() - height / 2),
                routeTransform
            )
        }
        targetSelfLocation.subscribeOn(lifecycle) {
            location = it.first
        }
        routePoint.subscribeOn(lifecycle) {
            repaint()
        }
        selfRouteTransform = targetSelfLocation.map { (pt, transform) ->
            AffineTransform().apply {
                translate(-pt.x.toDouble(), -pt.y.toDouble())
                concatenate(transform)
            }
        }
    }

    init {
        addComponentListener(object : ComponentListener {
            override fun componentResized(e: ComponentEvent?) {
                resizeEvents.value = Unit
            }

            override fun componentMoved(e: ComponentEvent?) {
                // nothing to do
            }

            override fun componentShown(e: ComponentEvent?) {
                // nothing to do
            }

            override fun componentHidden(e: ComponentEvent?) {
                // nothing to do
            }
        })
    }

    init {
        if (editGovernor is EditGovernor.Editable) {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseMotionListener(this)
            addMouseListener(this)
        }
    }

    private var isDragging = false
    override fun mouseDragged(e: MouseEvent) {
        if (editGovernor !is EditGovernor.Editable) {
            return
        }

        if (!isDragging) {
            isDragging = true
            cursor = CustomCursor.GRABBING
        }

        val pointedLocation = selfRouteTransform.value.inverseTransform(e.point, null).let {
            Vector3(it.x, it.y, 0.0)
        }

        val searchWindow = (segmentIndex.value - DRAG_SEARCH_HALF_WINDOW).coerceAtLeast(0)..
            (segmentIndex.value + DRAG_SEARCH_HALF_WINDOW).coerceAtMost(viewModel.segments.lastIndex)
        val indexOfClosestSegment = viewModel.getIndexOfSegmentClosestTo(pointedLocation, searchWindow)
        if (indexOfClosestSegment < 0 || !editGovernor.tryMoveTo(indexOfClosestSegment, atStart)) {
            return
        }

        segmentIndex.value = indexOfClosestSegment
    }

    override fun mouseReleased(e: MouseEvent?) {
        if (editGovernor !is EditGovernor.Editable) {
            return
        }

        if (isDragging) {
            isDragging = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
    }

    override fun mouseMoved(e: MouseEvent?) {
        // nothing to do
    }

    override fun mouseClicked(e: MouseEvent?) {
        // nothing to do
    }

    override fun mousePressed(e: MouseEvent?) {
        // nothing to do
    }

    override fun mouseEntered(e: MouseEvent?) {
        // nothing to do
    }

    override fun mouseExited(e: MouseEvent?) {
        // nothing to do
    }

    companion object {
        private val DRAG_SEARCH_HALF_WINDOW = ceil(75.0 / OPTIMAL_ROAD_SEGMENT_LENGTH).toInt()
    }

    sealed interface EditGovernor {
        object NotEditable : EditGovernor
        interface Editable : EditGovernor {
            /**
             * Called when the user has indicated movement to [segmentIndex]. Serves as both a callback/notification
             * and movement validity check
             * @param atStart pass-through of [EditableSinglePointOnRouteComponent.atStart]
             * @return whether the move is allowed; if false, the point will remain where it was previously
             */
            fun tryMoveTo(segmentIndex: Int, atStart: Boolean): Boolean
        }
    }
}