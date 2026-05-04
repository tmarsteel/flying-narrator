package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.operators.scan
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.UIRouteFeature
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.reactive.ReactiveJComponent
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.ui.toPoint
import io.github.tmarsteel.flyingnarrator.ui.withTransform
import io.github.tmarsteel.flyingnarrator.unit.Angle
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import javax.swing.Scrollable
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.roundToInt

class RouteComponent(
    val viewModel: RouteEditorViewModel,
) : ReactiveJComponent(), Scrollable {
    val routeStyling = mutableSignalOf(RouteStyling())
    val carMarker = mutableSignalOf(CarMarker())

    private val routeFeatures = mutableListOf<UIRouteFeature>()
    fun addRouteBoundComponent(component: UIRouteFeature) {
        if (routeFeatures.add(component)) {
            routeFeatures.sortBy { it.zIndex }
            component.onMounted(this)
        }
    }
    fun removeRouteBoundComponent(component: UIRouteFeature) {
        if (component in routeFeatures) {
            component.onUnmounted()
        }
        routeFeatures.remove(component)
    }

    val routeTransform = routeStyling.map { style ->
        AffineTransform().apply {
            translate(style.paddingPx.toDouble(), style.paddingPx.toDouble())
            scale(style.scale, -style.scale)
            translate(
                -viewModel.routeBounds.x,
                -(viewModel.routeBounds.y + viewModel.routeBounds.height)
            )
        }
    }

    private val baseImage: Signal<BufferedImage>
    init {
        val styleAndBuffer = routeStyling.scan(Pair(routeStyling.value, BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))) { (_, currentBaseImage), routeStyle ->
            val targetWidth = ceil(viewModel.routeBounds.width * routeStyle.scale).toInt() + routeStyle.paddingPx * 2
            val targetHeight = ceil(viewModel.routeBounds.height * routeStyle.scale).toInt() + routeStyle.paddingPx * 2
            var nextBaseImage = if (currentBaseImage.width == targetWidth && currentBaseImage.height == targetHeight) {
                currentBaseImage
            } else {
                BufferedImage(
                    targetWidth.coerceAtLeast(1),
                    targetHeight.coerceAtLeast(1),
                    BufferedImage.TYPE_INT_ARGB
                )
            }
            Pair(routeStyle, nextBaseImage)
        }
        baseImage = combine(styleAndBuffer, routeTransform) { (style, image), transform ->
            drawRoute(viewModel.segments, image, style, transform)
            image
        }
    }
    init {
        routeStyling.subscribeOn(lifecycle) {
            revalidate()
            repaint()
        }
        carMarker.subscribeOn(lifecycle) {
            repaint()
        }
    }

    var baseImageNeedsRepaint = true
    fun invalidateBaseImage() {
        baseImageNeedsRepaint = true
    }

    override fun getPreferredSize(): Dimension = preferredScrollableViewportSize

    override fun getMinimumSize(): Dimension = preferredScrollableViewportSize

    override fun getPreferredScrollableViewportSize(): Dimension {
        val style = routeStyling.value
        return Dimension(
            ceil(viewModel.routeBounds.width * style.scale).toInt() + style.paddingPx * 2,
            ceil(viewModel.routeBounds.height * style.scale).toInt() + style.paddingPx * 2,
        )
    }

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle?,
        orientation: Int,
        direction: Int
    ): Int = 1

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle?,
        orientation: Int,
        direction: Int
    ): Int = 1

    override fun getScrollableTracksViewportWidth(): Boolean = false
    override fun getScrollableTracksViewportHeight(): Boolean = false

    fun fitScaleToSize(targetWidth: Int, targetHeight: Int) {
        routeStyling.update { style ->
            val scaleX = (targetWidth.toDouble() - style.paddingPx * 2) / viewModel.routeBounds.width
            val scaleY = (targetHeight.toDouble() - style.paddingPx * 2) / viewModel.routeBounds.height
            style.copy(scale = scaleX.coerceAtMost(scaleY))
        }
    }

    override fun paintComponent(g: Graphics) {
        g as Graphics2D

        g.drawImage(baseImage.value, 0, 0, null)
    }

    override fun paintChildren(g: Graphics) {
        g as Graphics2D

        val subG = g.create() as Graphics2D
        subG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        try {
            val transform = subG.transform
            for (component in routeFeatures) {
                subG.transform = transform
                component.paint(subG)
            }
        } finally {
            subG.dispose()
        }

        paintCarMarker(g)

        super.paintChildren(g)

        subComponentState.paint(g)
    }

    private val carMarkerPositionPositionAndOrientation: Pair<Point, Angle>? by combine(carMarker, routeTransform) { marker, routeTransform ->
        if (marker.distanceAlongTrack < 0.meters) {
            return@combine null
        }

        val location = viewModel.findPreciseLocation(marker.distanceAlongTrack)
        if (location == null) {
            return@combine null
        }

        Pair(
            routeTransform.transform(location.point.toPoint2D(), null).toPoint(),
            location.segment.base.forward.clockwiseAngleFromPositiveY()
        )
    }

    private val carMarkerShape = Polygon(
        intArrayOf(
            5, 10, 5, 0
        ),
        intArrayOf(
            0, 10, 8, 10
        ),
        4
    )
    private fun paintCarMarker(g: Graphics2D) {
        val (markerPt, carMarkerOrientation) = carMarkerPositionPositionAndOrientation ?: return
        val carMarkerG = g.create() as Graphics2D
        carMarkerG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        try {
            carMarkerG.translate(markerPt.x, markerPt.y)
            carMarkerG.rotate(carMarkerOrientation.toDoubleInRadians())
            carMarkerG.scale(1.5, 1.5)
            carMarkerG.translate(-carMarkerShape.bounds.width / 2, -carMarkerShape.bounds.height / 2)
            carMarkerG.color = carMarker.value.color
            carMarkerG.fillPolygon(carMarkerShape)
            carMarkerG.color = Color.BLACK
            carMarkerG.stroke = BasicStroke(1.5f)
            carMarkerG.drawPolygon(carMarkerShape)
        }
        finally {
            carMarkerG.dispose()
        }
    }

    fun getRoutePositionFromComponentPosition(componentPosition: Point): Vector3 {
        val pt = routeTransform.value.inverseTransform(Point2D.Double(componentPosition.x.toDouble(), componentPosition.y.toDouble()), null)
        return Vector3(pt.x, pt.y, 0.0)
    }

    fun getComponentPositionFromRoutePosition(routePosition: Vector3): Point {
        val pt = routeTransform.value.transform(Point2D.Double(routePosition.x, routePosition.y), null)
        return Point(pt.x.roundToInt(), pt.y.roundToInt())
    }

    private interface SubComponentState {
        fun mouseMoved(e: MouseEvent) {}
        fun mouseClicked(e: MouseEvent) {}
        fun onKeyTyped(e: KeyEvent) {}
        fun paint(g: Graphics2D) {}
    }
    private val subComponentsIdleState = object : SubComponentState {
        override fun mouseMoved(e: MouseEvent) {
            val pointedLocation = toRouteSpace(e.point)
            for (component in routeFeatures.asReversed()) {
                if (component.shouldCapture(pointedLocation)) {
                    subComponentState = SubComponentHoveredState(component, e.point)
                    repaint()
                    break
                }
            }
        }

        override fun mouseClicked(e: MouseEvent) {
            mouseMoved(e)
            if (subComponentState != this) {
                e.consume()
                subComponentState.mouseClicked(e)
            }
        }
    }
    private inner class SubComponentHoveredState(
        val hovered: UIRouteFeature,
        hoverEnteredAt: Point,
    ) : SubComponentState {
        init {
            hovered.hovered.value = true
            if (hovered.isSelectable) {
                this@RouteComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
            }
            hovered.tooltip?.location = Point(hoverEnteredAt.x + TOOLTIP_OFFSET_X, hoverEnteredAt.y + TOOLTIP_OFFSET_Y)
            repaint()
        }

        override fun mouseMoved(e: MouseEvent) {
            val pointedLocation = toRouteSpace(e.point)
            for (component in routeFeatures.asReversed()) {
                if (component.shouldCapture(pointedLocation)) {
                    if (component === hovered) {
                        return
                    }
                    hovered.hovered.value = false
                    this@RouteComponent.setCursor(null)
                    subComponentState = SubComponentHoveredState(component, e.point)

                    return
                }
            }

            subComponentState = subComponentsIdleState
            hovered.hovered.value = false
            this@RouteComponent.setCursor(null)
            subComponentsIdleState.mouseMoved(e)
            repaint()
        }

        override fun mouseClicked(e: MouseEvent) {
            if (hovered.shouldCapture(toRouteSpace(e.point))) {
                subComponentState = SubComponentSelectedState(hovered)
                e.consume()
                repaint()
            }
        }

        override fun paint(g: Graphics2D) {
            hovered.tooltip?.let { tooltip ->
                withTransform(g, AffineTransform.getTranslateInstance(tooltip.x.toDouble(), tooltip.y.toDouble())) {
                    tooltip.paint(g)
                }
            }
        }
    }

    private inner class SubComponentSelectedState(
        val selected: UIRouteFeature,
    ) : SubComponentState {
        init {
            selected.hovered.value = false
            this@RouteComponent.setCursor(null)
            selected.selected.value = true
        }

        private fun deselect() {
            selected.selected.value = false
            subComponentState = subComponentsIdleState
        }

        override fun mouseClicked(e: MouseEvent) {
            if (!selected.shouldCapture(toRouteSpace(e.point))) {
                deselect()
                subComponentState.mouseClicked(e)
                e.consume()
            }
        }

        override fun onKeyTyped(e: KeyEvent) {
            if (e.isMetaDown || e.isShiftDown || e.isAltDown || e.isControlDown) {
                return
            }

            if (e.keyChar != '\u001B') {
                return
            }

            deselect()
            e.consume()
        }
    }

    private var subComponentState: SubComponentState = subComponentsIdleState

    private fun toRouteSpace(point: Point): Vector3 {
        return routeTransform.value.inverseTransform(point, null).let {
            Vector3(it.x, it.y, 0.0)
        }
    }

    private val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val listener = object : MouseListener, MouseMotionListener, KeyEventDispatcher {
        override fun mouseMoved(e: MouseEvent) {
            subComponentState.mouseMoved(e)
        }
        override fun mouseDragged(e: MouseEvent?) {}

        override fun mouseClicked(e: MouseEvent) {
            subComponentState.mouseClicked(e)
        }
        override fun mousePressed(e: MouseEvent?) {}
        override fun mouseReleased(e: MouseEvent?) {}
        override fun mouseEntered(e: MouseEvent?) {}
        override fun mouseExited(e: MouseEvent?) {}

        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            val selfWindow = SwingUtilities.getWindowAncestor(this@RouteComponent)
            if (selfWindow.isActive && e.component?.let { SwingUtilities.getWindowAncestor(it) } != selfWindow) {
                return false
            }

            if (e.id != KeyEvent.KEY_TYPED) {
                return false
            }

            subComponentState.onKeyTyped(e)

            return e.isConsumed
        }
    }
    init {
        addMouseListener(listener)
        addMouseMotionListener(listener)
        keyboardFocusManager.addKeyEventDispatcher(listener)
    }

    override fun removeNotify() {
        super.removeNotify()
        keyboardFocusManager.removeKeyEventDispatcher(listener)
    }

    data class RouteStyling(
        val scale: Double = 0.4,
        val paddingPx: Int = 100,
        val distanceMarkersEvery: Distance = 200.meters,
        val distanceMarkerColor: Color? = Color.RED,
        val trackWidth: Distance = 5.meters,
        val trackColor: Color = Color.BLACK,
    )

    data class CarMarker(
        val distanceAlongTrack: Distance = -(1.meters),
        val color: Color = Color(0xEC003D),
    )

    companion object {
        const val TOOLTIP_OFFSET_X = 15
        const val TOOLTIP_OFFSET_Y = 15

        private fun drawRoute(route: List<RouteEditorViewModel.RouteSegmentModel>, baseImage: BufferedImage, routeStyle: RouteStyling, routeTransform: AffineTransform) {
            val g = baseImage.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            g.transform(routeTransform)
            g.stroke = BasicStroke(routeStyle.trackWidth.toDoubleInMeters().toFloat())
            var carryPoint = Vector3.ORIGIN
            var prevX = carryPoint.x
            var prevY = carryPoint.y
            var lastDistanceMarkerAt = 0.meters
            for (segment in route) {
                carryPoint += segment.base.forward

                val x = carryPoint.x
                val y = carryPoint.y

                val lineLength = Vector3(prevX - x, prevY - y, 0.0).length2d
                val drawThisLine = lineLength > routeStyle.trackWidth.toDoubleInMeters() * 1.75
                if (drawThisLine) {
                    g.color = routeStyle.trackColor
                    g.draw(Line2D.Double(prevX, prevY, x, y))
                }

                if (segment.startsAtDistance - lastDistanceMarkerAt >= routeStyle.distanceMarkersEvery && routeStyle.distanceMarkerColor != null) {
                    lastDistanceMarkerAt = segment.startsAtDistance
                    val labelPt = routeTransform.transform(Point2D.Double(x, y), null)
                    withTransform(g, AffineTransform()) {
                        g.color = routeStyle.distanceMarkerColor
                        g.drawString(segment.startsAtDistance.toString(), (labelPt.x + 30).toInt(), (labelPt.y + 10).toInt())
                    }
                }

                if (drawThisLine) {
                    prevX = x
                    prevY = y
                }
            }

            val finishPt = routeTransform.transform(Point2D.Double(prevX, prevY), null)
            withTransform(g, AffineTransform()) {
                val finalDistance = route.last().let{ it.startsAtDistance + it.base.length }
                g.color = routeStyle.distanceMarkerColor
                g.drawString(finalDistance.toString(), finishPt.x.toInt(), (finishPt.y + 10).toInt())
            }

            g.dispose()
        }
    }
}