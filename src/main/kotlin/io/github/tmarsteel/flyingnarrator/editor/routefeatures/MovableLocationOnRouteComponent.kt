package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.MutableSignal
import io.github.tmarsteel.flyingnarrator.editor.RouteViewModel
import io.github.tmarsteel.flyingnarrator.feature.OPTIMAL_ROAD_SEGMENT_LENGTH
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.CustomCursor
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import kotlin.math.ceil

abstract class MovableLocationOnRouteComponent(
    val editableLocation: MutableSignal<RouteViewModel.PreciseLocation>,
    val moveGovernor: MoveGovernor,
) : LocationOnRouteComponent(editableLocation), MouseListener, MouseMotionListener {
    init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseMotionListener(this)
        addMouseListener(this)
    }

    private var isDragging = false
    override fun mouseDragged(e: MouseEvent) {
        if (!isDragging) {
            isDragging = true
            cursor = CustomCursor.GRABBING
            moveGovernor.onInteractiveMovementStarted(locationOnRoute.value)
        }

        val pointedLocation = selfRouteTransform.value.inverseTransform(e.point, null).let {
            Vector3(it.x, it.y, 0.0)
        }

        val routeModel = expectParentRouteComponent().routeModel
        val searchWindow = (locationOnRoute.value.segment.index - DRAG_SEARCH_HALF_WINDOW).coerceAtLeast(0)..
            (locationOnRoute.value.segment.index + DRAG_SEARCH_HALF_WINDOW).coerceAtMost(routeModel.segments.lastIndex)
        val closestLocation = routeModel.findPreciseLocationClosestTo(pointedLocation, searchWindow)
            ?: return
        val processedLocation = moveGovernor.processPotentialMove(closestLocation)
            ?: return

        editableLocation.value = processedLocation
    }

    override fun mouseReleased(e: MouseEvent?) {
        if (isDragging) {
            isDragging = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            moveGovernor.onInteractiveMovementFinished()
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

    interface MoveGovernor {
        /**
         * called when the user starts interactively changing the location of this component
         * @param previousRestingLocation the location on the route this component had before interactive location
         * editing started.
         */
        fun onInteractiveMovementStarted(previousRestingLocation: RouteViewModel.PreciseLocation) {}

        /**
         * called when the user finishes interactively changing the location of this component
         */
        fun onInteractiveMovementFinished() {}

        /**
         * Called when the user has indicated movement to [location], possibly between calls to[onInteractiveMovementStarted]
         * and [onInteractiveMovementFinished]. This function filters out invalid new locations, returning `null` if
         * the indicated position is not valid. Also, the function can implement snapping by returning an altered
         * location.
         */
        fun processPotentialMove(location: RouteViewModel.PreciseLocation): RouteViewModel.PreciseLocation?

        class FreelyMovable(
            /**
             * Movement updates to [onMoveIndicated] within interactive edits ([onInteractiveMovementStarted]) will only
             * be accepted after the new indicated location is at least this distance away.
             */
            val interactiveInertia: Distance = 50.meters
        ) : MoveGovernor {
            private var initialInertiaBroken = false
            private var interactiveEditStartedAt: RouteViewModel.PreciseLocation? = null

            override fun onInteractiveMovementStarted(previousRestingLocation: RouteViewModel.PreciseLocation) {
                initialInertiaBroken = false
                interactiveEditStartedAt = previousRestingLocation
            }

            override fun onInteractiveMovementFinished() {
                initialInertiaBroken = false
                interactiveEditStartedAt = null
            }

            override fun processPotentialMove(location: RouteViewModel.PreciseLocation): RouteViewModel.PreciseLocation? {
                if (interactiveEditStartedAt != null && !initialInertiaBroken) {
                    val distance = (location.distanceAlongRoute - interactiveEditStartedAt!!.distanceAlongRoute).absoluteValue
                    if (distance < interactiveInertia) {
                        return null
                    }
                    initialInertiaBroken = true
                }

                return location
            }
        }
    }

    companion object {
        private val DRAG_SEARCH_HALF_WINDOW = ceil(75.0 / OPTIMAL_ROAD_SEGMENT_LENGTH).toInt()
    }
}