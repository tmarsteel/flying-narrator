package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.CustomCursor
import java.awt.Cursor
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import javax.swing.JComponent
import kotlin.math.roundToInt

/**
 * Represents a single point on the route. It assumes that it is a child of [RouteComponent] and will always position
 * itself so that the center of the component is at the track location determined from [segmentIndex] and [atStart].
 *
 * // TODO: parameterize possibility of moving
 */
abstract class SinglePointOnRouteComponent(
    val viewModel: RouteEditorViewModel,
    initialSegmentIndex: Int,
    val atStart: Boolean,
    val editGovernor: EditGovernor = EditGovernor.NotEditable,
    /**
     * @see routeTransform
     */
    initialRouteTransform: AffineTransform = AffineTransform()
) : JComponent(), MouseListener, MouseMotionListener {
    /**
     * Transforms from route coordinate space to the pixel space of the parent [RouteComponent].
     * **Must be kept up to date as the [RouteComponent] zooms etc.**
     * TODO: automatically propagate through the view model??
     */
    var routeTransform: AffineTransform = initialRouteTransform
        set(value) {
            field = value
            centerOnTrackPoint()
        }

    private lateinit var routePoint: Point2D
    private var segmentIndex: Int = -1
        set(value) {
            check(value in viewModel.route.indices)
            field = value
            routePoint = viewModel.mathSegments[value]
                .let { if (atStart) it.startPoint else it.endPoint }
                .toPoint2D()
            centerOnTrackPoint()
        }
    init {
        // force proper initialization through setter
        segmentIndex = initialSegmentIndex
    }

    private lateinit var selfRouteTransform: AffineTransform

    /**
     * Call to update [x] and [y] so that the center of the component is at the point on the route
     */
    private fun centerOnTrackPoint() {
        val locationAsDouble = routeTransform.transform(routePoint, null)
        setLocation(locationAsDouble.x.roundToInt() - width / 2, locationAsDouble.y.roundToInt() - height / 2)
        selfRouteTransform = AffineTransform().apply {
            translate(-x.toDouble(), -y.toDouble())
            concatenate(routeTransform)
        }
    }

    init {
        addComponentListener(object : ComponentListener {
            override fun componentResized(e: ComponentEvent?) {
                centerOnTrackPoint()
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
        centerOnTrackPoint()
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

        val pointedLocation = selfRouteTransform.inverseTransform(e.point, null).let {
            Vector3(it.x, it.y, 0.0)
        }

        val indexOfClosestSegment = viewModel.getIndexOfSegmentClosestTo(pointedLocation)
        if (indexOfClosestSegment < 0 || !editGovernor.tryMoveTo(indexOfClosestSegment, atStart)) {
            return
        }

        println("$segmentIndex -> $indexOfClosestSegment")
        segmentIndex = indexOfClosestSegment
        repaint()
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

    sealed interface EditGovernor {
        object NotEditable : EditGovernor
        interface Editable : EditGovernor {
            /**
             * Called when the user has indicated movement to [segmentIndex]. Serves as both a callback/notification
             * and movement validity check
             * @param atStart pass-through of [SinglePointOnRouteComponent.atStart]
             * @return whether the move is allowed; if false, the point will remain where it was previously
             */
            fun tryMoveTo(segmentIndex: Int, atStart: Boolean): Boolean
        }
    }
}