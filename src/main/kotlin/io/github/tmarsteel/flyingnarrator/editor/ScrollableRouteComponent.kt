package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.OverlayLayout
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

/**
 * Adds these features to a [RouteComponent]:
 * * scrolling vertically and horizontally using [JScrollPane]
 * * scrolling vertically and horizontally using mouse click+drag
 * * scaling/zooming using dedicated zooming controls and the mouse wheel
 * * a scale indicator
 */
class ScrollableRouteComponent(
    private val routeComponent: RouteComponent,
) : JLayeredPane() {
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
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        override fun mouseReleased(e: MouseEvent?) {
            dragStartedAt = null
            setCursor(null)
        }

        override fun mouseDragged(e: MouseEvent) {
            check(dragStartedAt != null && viewportPositionAtDragStart != null)
            val dragDeltaX = e.point.x - dragStartedAt!!.x
            val dragDeltaY = e.point.y - dragStartedAt!!.y
            val newViewportPositionX = (viewportPositionAtDragStart!!.x - dragDeltaX)
                .coerceAtLeast(0)
            val newViewportPositionY = (viewportPositionAtDragStart!!.y - dragDeltaY)
                .coerceAtLeast(0)
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
            zoomComponent.update()
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

    private val scaleIndicatorComponent = object : JComponent() {
        private val indicatedDistance = 100.meters
        private val fontSize = 14.0f

        private val indicatorLineLength: Int
            get() = (routeComponent.scale * indicatedDistance.toDoubleInMeters()).toInt()

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            g as Graphics2D

            g.color = Color.BLACK // todo: use track color
            g.stroke = BasicStroke(routeComponent.lineThickness)
            g.drawLine(0, 0, indicatorLineLength, 0)
            g.font = g.font.deriveFont(fontSize)
            g.drawString("100m", 0, (routeComponent.lineThickness * 2.0f + fontSize).toInt())
        }

        override fun getPreferredSize(): Dimension? {
            return getMinimumSize()
        }

        override fun getMinimumSize(): Dimension {
            return Dimension(
                indicatorLineLength,
                (routeComponent.lineThickness * 2.0f + fontSize).toInt(),
            )
        }
    }

    private val zoomComponent = object : JPanel() {
        private val zoomOutButton = JButton("-")
        private val zoomInButton = JButton("+")
        private val zoomLabel = JLabel("100%")
        init {
            layout = BoxLayout(this, BoxLayout.LINE_AXIS)
            add(zoomOutButton)
            add(Box.createHorizontalStrut(3))
            add(zoomLabel)
            add(Box.createHorizontalStrut(3))
            add(zoomInButton)
            isOpaque = false
            update()

            zoomInButton.addActionListener {
                routeComponent.scale *= 1.1
                update()
            }
            zoomOutButton.addActionListener {
                routeComponent.scale /= 1.1
                update()
            }
        }

        fun update() {
            zoomLabel.text = "${(routeComponent.scale * 100.0).toInt()}%"
        }

        override fun revalidate() {
            super.revalidate()
        }
    }

    init {
        layout = OverlayLayout(this)
        scrollPane.isWheelScrollingEnabled = false
        scrollPane.viewport.scrollMode = JViewport.BLIT_SCROLL_MODE
        setLayer(scrollPane, 0)
        add(scrollPane)

        val controlsPanel = JPanel().also {
            val gridBag = GridBagLayout()
            it.layout = gridBag
            it.isOpaque = false
            it.isEnabled = false
            it.background = Color(0, true)
            gridBag.setConstraints(scaleIndicatorComponent, GridBagConstraints().apply {
                weightx = 1.0
                weighty = 1.0
                insets = Insets(10, 10, 10, 10)
                anchor = GridBagConstraints.SOUTHWEST
            })
            it.add(scaleIndicatorComponent)
            gridBag.setConstraints(zoomComponent, GridBagConstraints().apply {
                weightx = 1.0
                weighty = 1.0
                insets = Insets(10, 10, 10, 10)
                anchor = GridBagConstraints.SOUTHEAST
            })
            it.add(zoomComponent)
        }
        setLayer(controlsPanel, 1)
        add(controlsPanel)

        routeComponent.addMouseListener(_mouseListener)
        routeComponent.addMouseMotionListener(_mouseListener)
        routeComponent.addMouseWheelListener(_mouseListener)
    }

    fun fitScaleToSize() {
        routeComponent.fitScaleToSize(width, height)
    }
}