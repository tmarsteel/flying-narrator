package io.github.tmarsteel.flyingnarrator.editor

import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComponent
import javax.swing.JScrollPane

/**
 * A [javax.swing.JScrollPane], but it also scrolls on mouse drag.
 */
class JScrollPaneWithDragScroll(
    view: JComponent,
    vsbPolicy: Int = VERTICAL_SCROLLBAR_NEVER,
    hsbPolicy: Int = HORIZONTAL_SCROLLBAR_NEVER,
) : JScrollPane(view, vsbPolicy, hsbPolicy) {

    private val dragMoveHandler = object : MouseListener, MouseMotionListener {
        var dragStartedAt: Point? = null
        var viewportPositionAtDragStart: Point? = null
        override fun mousePressed(e: MouseEvent) {
            dragStartedAt = e.point
            viewportPositionAtDragStart = viewport.viewPosition
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
            viewport.viewPosition = Point(newViewportPositionX, newViewportPositionY)
        }

        override fun mouseEntered(e: MouseEvent?) {}
        override fun mouseExited(e: MouseEvent?) {}
        override fun mouseMoved(e: MouseEvent?) {}
        override fun mouseClicked(e: MouseEvent?) {}
    }

    init {
        addMouseListener(dragMoveHandler)
        addMouseMotionListener(dragMoveHandler)
    }
}