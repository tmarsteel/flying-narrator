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
abstract class PointOnTrackEditHandle(
    val viewModel: RouteEditorViewModel,
    initialLocation: RouteEditorViewModel.PreciseLocation,
    val snapping: Snapping,
    val editGovernor: EditGovernor = EditGovernor.NotEditable,
) : ReactiveRouteComponentChild(), MouseListener, MouseMotionListener {
    private val routeLocation = mutableSignalOf(initialLocation)

    private val resizeEvents = mutableSignalOf(Unit)
    private val selfRouteTransform: Signal<AffineTransform>
    init {
        val targetSelfLocation = combine(
            routeLocation,
            parentRouteComponent.flatMap { pc -> pc?.routeTransform ?: signalOf(AffineTransform()) },
            resizeEvents,
        ) { routeLocation, routeTransform, _ ->
            val locationAsDouble = routeTransform.transform(routeLocation.point, null)
            Pair(
                Point(locationAsDouble.x.roundToInt() - width / 2, locationAsDouble.y.roundToInt() - height / 2),
                routeTransform
            )
        }
        targetSelfLocation.subscribeOn(lifecycle) {
            location = it.first
        }
        routeLocation.subscribeOn(lifecycle) {
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

        if (editGovernor is EditGovernor.Editable) {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseMotionListener(this)
            addMouseListener(this)
        }
    }

    private var isDragging = false
    private var draggingStartedAt = Point()
    private var initialInertiaBroken = false
    override fun mouseDragged(e: MouseEvent) {
        if (editGovernor !is EditGovernor.Editable) {
            return
        }

        if (!isDragging) {
            isDragging = true
            draggingStartedAt = e.point
            initialInertiaBroken = false
            cursor = CustomCursor.GRABBING
        }

        if (!initialInertiaBroken) {
            val distance = e.point.distance(draggingStartedAt).toInt()
            if (distance < editGovernor.startEditingAfterMovementOfPixels) {
                return
            }
            initialInertiaBroken = true
        }

        val pointedLocation = selfRouteTransform.value.inverseTransform(e.point, null).let {
            Vector3(it.x, it.y, 0.0)
        }

        val searchWindow = (routeLocation.value.segment.index - DRAG_SEARCH_HALF_WINDOW).coerceAtLeast(0)..
            (routeLocation.value.segment.index + DRAG_SEARCH_HALF_WINDOW).coerceAtMost(viewModel.segments.lastIndex)
        val closestLocation = viewModel.findPreciseLocationClosestTo(pointedLocation, searchWindow)
            ?: return
        val snappedLocation = snapping.getSnappedLocation(closestLocation)
        if (!editGovernor.tryMoveTo(snappedLocation)) {
            return
        }

        routeLocation.value = snappedLocation
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
             * The drag-to-move will only start when the mouse has been dragged at least this number of pixels away from
             * where the dragging started.
             */
            val startEditingAfterMovementOfPixels: Int get()= 30

            /**
             * Called when the user has indicated movement to [segmentIndex]. Serves as both a callback/notification
             * and movement validity check
             * @param location the location to move to, after [Snapping] has been applied
             * @return whether the move is allowed; if false, the point will remain where it was previously
             */
            fun tryMoveTo(location: RouteEditorViewModel.PreciseLocation): Boolean
        }
    }

    interface Snapping {
        fun getSnappedLocation(trueLocation: RouteEditorViewModel.PreciseLocation): RouteEditorViewModel.PreciseLocation

        object ToSegmentStart : Snapping {
            override fun getSnappedLocation(trueLocation: RouteEditorViewModel.PreciseLocation): RouteEditorViewModel.PreciseLocation {
                return trueLocation.atSegmentStart()
            }
        }

        object ToSegmentEnd : Snapping {
            override fun getSnappedLocation(trueLocation: RouteEditorViewModel.PreciseLocation): RouteEditorViewModel.PreciseLocation {
                return trueLocation.atSegmentEnd()
            }
        }

        object FreeMovement : Snapping {
            override fun getSnappedLocation(trueLocation: RouteEditorViewModel.PreciseLocation): RouteEditorViewModel.PreciseLocation {
                return trueLocation
            }
        }
    }
}