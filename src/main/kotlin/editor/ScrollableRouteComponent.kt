package io.github.tmarsteel.flyingnarrator.editor

import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

class ScrollableRouteComponent(
    private val routeComponent: RouteComponent,
) : JPanel() {
    private val scrollPane = JScrollPane(
        routeComponent,
        VERTICAL_SCROLLBAR_AS_NEEDED,
        HORIZONTAL_SCROLLBAR_AS_NEEDED
    )

    private val _mouseListener = object : MouseListener, MouseMotionListener, MouseWheelListener {
        val scrollZoomDivisor = routeComponent.routeBoundsInRouteCoordinateSpace.width
            .coerceAtLeast(routeComponent.routeBoundsInRouteCoordinateSpace.height)
            .let { it / 100.0 }

        var dragStartedAt: Point? = null
        var viewportPositionAtDragStart: Point? = null
        override fun mousePressed(e: MouseEvent) {
            dragStartedAt = e.point
            viewportPositionAtDragStart = scrollPane.viewport.viewPosition
        }

        override fun mouseReleased(e: MouseEvent?) {
            dragStartedAt = null
        }

        override fun mouseDragged(e: MouseEvent) {
            check(dragStartedAt != null && viewportPositionAtDragStart != null)
            val dragDeltaX = e.point.x - dragStartedAt!!.x
            val dragDeltaY = e.point.y - dragStartedAt!!.y
            val newViewportPositionX = (viewportPositionAtDragStart!!.x - dragDeltaX).coerceAtLeast(0)
            val newViewportPositionY = (viewportPositionAtDragStart!!.y - dragDeltaY).coerceAtLeast(0)
            scrollPane.viewport.viewPosition = Point(newViewportPositionX, newViewportPositionY)
        }

        override fun mouseWheelMoved(mwe: MouseWheelEvent) {
            val newScale = (routeComponent.scale - mwe.preciseWheelRotation / scrollZoomDivisor).coerceAtLeast(0.1)
            if (routeComponent.scale == newScale) {
                return
            }
            val pointedComponentPosition = Point(mwe.x, mwe.y)
            val pointedRoutePosition = routeComponent.getRoutePositionFromComponentPosition(pointedComponentPosition)
            routeComponent.scale = newScale
            val newComponentPosition = routeComponent.getComponentPositionFromRoutePosition(pointedRoutePosition)
            val dX = newComponentPosition.x - pointedComponentPosition.x
            val dY = newComponentPosition.y - pointedComponentPosition.y
            scrollPane.viewport.viewPosition = Point(scrollPane.viewport.viewPosition.x + dX, scrollPane.viewport.viewPosition.y + dY)
        }

        override fun mouseEntered(e: MouseEvent?) {}
        override fun mouseExited(e: MouseEvent?) {}
        override fun mouseMoved(e: MouseEvent?) {}
        override fun mouseClicked(e: MouseEvent?) {}
    }

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        scrollPane.isWheelScrollingEnabled = false
        scrollPane.viewport.scrollMode = JViewport.BLIT_SCROLL_MODE
        add(scrollPane)
        routeComponent.addMouseListener(_mouseListener)
        routeComponent.addMouseMotionListener(_mouseListener)
        routeComponent.addMouseWheelListener(_mouseListener)
    }
}