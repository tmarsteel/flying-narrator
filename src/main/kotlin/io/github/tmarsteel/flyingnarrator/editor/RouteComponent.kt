package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.ui.withTransform
import io.github.tmarsteel.flyingnarrator.unit.Angle
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.radians
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.Scrollable
import kotlin.math.ceil
import kotlin.math.roundToInt

class RouteComponent(
    val route: Route,
) : JComponent(), Scrollable {
    var scale by RepaintBaseImageOnChange(0.4, alsoOnChange = { revalidate() })
    var distanceMarkersEvery by RepaintBaseImageOnChange(200.meters)
    var distanceMarkerColor: Color? by RepaintBaseImageOnChange(Color.RED)
    var lineThickness by RepaintBaseImageOnChange(3.0f)
    var paddingPx by RepaintBaseImageOnChange(100, alsoOnChange = { revalidate() })
    var trackColor: Color by RepaintBaseImageOnChange(Color.BLACK)

    val routeBoundsInRouteCoordinateSpace: Rectangle2D = computeRouteBounds(route)

    private fun buildRouteTransform(): AffineTransform {
        val t = AffineTransform()
        t.translate(paddingPx.toDouble(), paddingPx.toDouble())
        t.scale(scale, -scale)
        t.translate(-routeBoundsInRouteCoordinateSpace.x, -(routeBoundsInRouteCoordinateSpace.y + routeBoundsInRouteCoordinateSpace.height))
        return t
    }

    var baseImageNeedsRepaint = true
    fun invalidateBaseImage() {
        baseImageNeedsRepaint = true
    }

    override fun getPreferredSize(): Dimension = preferredScrollableViewportSize

    override fun getMinimumSize(): Dimension = preferredScrollableViewportSize

    override fun getPreferredScrollableViewportSize(): Dimension {
        return Dimension(
            ceil(routeBoundsInRouteCoordinateSpace.width * scale).toInt() + paddingPx * 2,
            ceil(routeBoundsInRouteCoordinateSpace.height * scale).toInt() + paddingPx * 2,
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
        val scaleX = (targetWidth.toDouble() - paddingPx * 2) / routeBoundsInRouteCoordinateSpace.width
        val scaleY = (targetHeight.toDouble() - paddingPx * 2) / routeBoundsInRouteCoordinateSpace.height
        scale = scaleX.coerceAtMost(scaleY)
    }

    override fun paintComponent(g: Graphics) {
        g as Graphics2D
        assureBaseImageIsUpToDate()

        g.drawImage(baseImage, 0, 0, null)
    }

    override fun paintChildren(g: Graphics) {
        super.paintChildren(g)

        g as Graphics2D

        val subG = g.create() as Graphics2D
        subG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val routeTransform = buildRouteTransform()
        try {
            for (component in routeBoundComponents) {
                component.paint(subG, routeTransform)
            }
        } finally {
            subG.dispose()
        }

        paintCarMarker(g)

        hoveredComponent?.tooltip?.let { tooltip ->
            withTransform(g, AffineTransform.getTranslateInstance((tooltip.x + 15).toDouble(), (tooltip.y + 15).toDouble())) {
                tooltip.paint(g)
            }
        }
    }

    var carPositionOnTrack: Distance = -(1.meters)
        set(value) {
            field = value
            updateCarMarkerState()
            repaint()
        }
    private var carMarkerPositionInTrackCoords: Vector3 = Vector3.ORIGIN
    private var carMarkerOrientation: Angle = 0.radians
    private fun updateCarMarkerState() {
        if (carPositionOnTrack < 0.meters) {
            return
        }
        var distanceCarry = 0.meters
        var positionCarry = Vector3.ORIGIN
        for (segment in route) {
            val nextDistanceCarry = distanceCarry + segment.length
            if (carPositionOnTrack in distanceCarry..nextDistanceCarry) {
                carMarkerPositionInTrackCoords = positionCarry + segment.forward.withLength((carPositionOnTrack - distanceCarry).toDoubleInMeters())
                carMarkerOrientation = segment.forward.clockwiseAngleFromPositiveY()
                break
            }

            positionCarry += segment.forward
            distanceCarry = nextDistanceCarry
        }
    }
    var carMarkerColor: Color = Color(0xEC003D)
        set(value) {
            field = value
            repaint()
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
        if (carPositionOnTrack < 0.meters) {
            return
        }

        val markerPt = buildRouteTransform().transform(
            Point2D.Double(carMarkerPositionInTrackCoords.x, carMarkerPositionInTrackCoords.y),
            null,
        )

        val carMarkerG = g.create() as Graphics2D
        carMarkerG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        try {
            carMarkerG.translate(markerPt.x, markerPt.y)
            carMarkerG.rotate(carMarkerOrientation.toDoubleInRadians())
            carMarkerG.scale(1.5, 1.5)
            carMarkerG.translate(-carMarkerShape.bounds.width / 2, -carMarkerShape.bounds.height / 2)
            carMarkerG.color = carMarkerColor
            carMarkerG.fillPolygon(carMarkerShape)
            carMarkerG.color = Color.BLACK
            carMarkerG.stroke = BasicStroke(1.5f)
            carMarkerG.drawPolygon(carMarkerShape)
        }
        finally {
            carMarkerG.dispose()
        }
    }

    private val routeBoundComponents = mutableListOf<RouteBoundComponent>()
    fun addRouteBoundComponent(component: RouteBoundComponent) {
        routeBoundComponents.add(component)
    }
    fun removeRouteBoundComponent(component: RouteBoundComponent) {
        routeBoundComponents.remove(component)
    }

    private lateinit var baseImage: BufferedImage
    private fun assureBaseImageIsUpToDate() {
        val targetWidth = ceil(routeBoundsInRouteCoordinateSpace.width * scale).toInt() + paddingPx * 2
        val targetHeight = ceil(routeBoundsInRouteCoordinateSpace.height * scale).toInt() + paddingPx * 2
        if (!baseImageNeedsRepaint && this::baseImage.isInitialized) {
            return
        }
        if (!this::baseImage.isInitialized || this.baseImage.width != targetWidth || this.baseImage.height != targetHeight) {
            baseImage = BufferedImage(
                targetWidth.coerceAtLeast(1),
                targetHeight.coerceAtLeast(1),
                BufferedImage.TYPE_INT_RGB
            )
        }

        val bgColor = Color.WHITE
        val g = baseImage.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = bgColor
        g.fillRect(0, 0, baseImage.width, baseImage.height)

        val routeTransform = buildRouteTransform()
        g.transform(routeTransform)
        // stroke width is in user space; divide by scale so it renders as lineThickness pixels
        g.stroke = BasicStroke((lineThickness / scale).toFloat())
        var carryPoint = Vector3.ORIGIN
        var prevX = carryPoint.x
        var prevY = carryPoint.y
        var distanceCarry = 0.meters
        var distanceSinceLastMarker = 0.meters
        for (segment in route) {
            carryPoint += segment.forward

            val x = carryPoint.x
            val y = carryPoint.y

            val lineLength = Vector3(prevX - x, prevY - y, 0.0).length2d
            val drawThisLine = lineLength * scale > (lineThickness * 1.75)
            if (drawThisLine) {
                g.color = trackColor
                g.draw(Line2D.Double(prevX, prevY, x, y))
            }

            distanceCarry += segment.length
            distanceSinceLastMarker += segment.length
            if (distanceSinceLastMarker >= distanceMarkersEvery && distanceMarkerColor != null) {
                distanceSinceLastMarker = 0.meters
                val labelPt = routeTransform.transform(Point2D.Double(x, y), null)
                withTransform(g, AffineTransform()) {
                    g.color = distanceMarkerColor
                    g.drawString(distanceCarry.toString(), (labelPt.x + 30).toInt(), (labelPt.y + 10).toInt())
                }
            }

            if (drawThisLine) {
                prevX = x
                prevY = y
            }
        }

        val finishPt = routeTransform.transform(Point2D.Double(prevX, prevY), null)
        withTransform(g, AffineTransform()) {
            g.color = distanceMarkerColor
            val distanceText = String.format("%3.2f km", (distanceCarry.toDoubleInMeters() / 1000.0))
            g.drawString(distanceText, finishPt.x.toInt(), (finishPt.y + 10).toInt())
        }

        g.dispose()
        baseImageNeedsRepaint = false
    }

    fun getRoutePositionFromComponentPosition(componentPosition: Point): Vector3 {
        val pt = buildRouteTransform().inverseTransform(Point2D.Double(componentPosition.x.toDouble(), componentPosition.y.toDouble()), null)
        return Vector3(pt.x, pt.y, 0.0)
    }

    fun getComponentPositionFromRoutePosition(routePosition: Vector3): Point {
        val pt = buildRouteTransform().transform(Point2D.Double(routePosition.x, routePosition.y), null)
        return Point(pt.x.roundToInt(), pt.y.roundToInt())
    }

    private inner class RepaintBaseImageOnChange<T>(
        initial: T,
        val alsoOnChange: (T) -> Unit = {},
    ) {
        private var value: T = initial
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
            val changed = this.value != value
            this.value = value
            if (changed) {
                invalidateBaseImage()
                repaint()
                alsoOnChange(value)
            }
        }
    }

    private var hoveredComponent: RouteBoundComponent? = null

    init {
        addMouseMotionListener(object : MouseMotionListener {
            override fun mouseMoved(e: MouseEvent) {
                val pointedLocation = buildRouteTransform().inverseTransform(e.point, null).let {
                    Vector3(it.x, it.y, 0.0)
                }
                var nowHovered: RouteBoundComponent? = null
                for (component in routeBoundComponents) {
                    if (nowHovered != null) {
                        component.isHovered = false
                    } else if (component.tryClaimHover(pointedLocation)) {
                        nowHovered = component
                        component.isHovered = true
                    } else {
                        component.isHovered = false
                    }
                }
                if (nowHovered != null && hoveredComponent == null && nowHovered.isSelectable) {
                    this@RouteComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                } else if (nowHovered == null && hoveredComponent != null) {
                    this@RouteComponent.setCursor(null)
                }
                hoveredComponent = nowHovered
                hoveredComponent?.tooltip?.location = e.point
                repaint()
            }

            override fun mouseDragged(e: MouseEvent?) {}
        })
    }
}

private fun computeRouteBounds(route: Route): Rectangle2D.Double {
    var minX = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    route.fold(Vector3.ORIGIN) { carryPt, segment ->
        val nextCarry = carryPt + segment.forward
        minX = minX.coerceAtMost(nextCarry.x)
        maxX = maxX.coerceAtLeast(nextCarry.x)
        minY = minY.coerceAtMost(nextCarry.y)
        maxY = maxY.coerceAtLeast(nextCarry.y)
        nextCarry
    }
    return Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY)
}