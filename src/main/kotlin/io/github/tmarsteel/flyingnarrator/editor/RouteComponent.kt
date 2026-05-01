package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.pacenote.inferred.cornerFeatureToPacenoteItem
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.unit.Angle
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.radians
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.utils.foldInto
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JToolTip
import kotlin.math.ceil
import kotlin.math.roundToInt

class RouteComponent(
    val route: Route,
    val features: List<Feature>,
) : JComponent() {
    var scale by RepaintBaseImageOnChange(0.4, alsoOnChange = { revalidate() })
    var distanceMarkersEvery by RepaintBaseImageOnChange(200.meters)
    var distanceMarkerColor: Color? by RepaintBaseImageOnChange(Color.RED)
    var startMarkerColor by RepaintBaseImageOnChange(Color.RED)
    var finishMarkerColor by RepaintBaseImageOnChange(Color.GREEN)
    var lineThickness by RepaintBaseImageOnChange(3.0f)
    var segmentJointMarkerColor: Color? by RepaintBaseImageOnChange(null)
    var paddingPx by RepaintBaseImageOnChange(100, alsoOnChange = { revalidate() })

    private val routeBounds = object {
        val minX: Double
        val maxX: Double
        val minY: Double
        val maxY: Double

        init {
            var localMinX = Double.POSITIVE_INFINITY
            var localMaxX = Double.NEGATIVE_INFINITY
            var localMinY = Double.POSITIVE_INFINITY
            var localMaxY = Double.NEGATIVE_INFINITY
            route.fold(Vector3.ORIGIN) { carryPt, segment ->
                val nextCarry = carryPt + segment.forward
                localMinX = localMinX.coerceAtMost(nextCarry.x)
                localMaxX = localMaxX.coerceAtLeast(nextCarry.x)
                localMinY = localMinY.coerceAtMost(nextCarry.y)
                localMaxY = localMaxY.coerceAtLeast(nextCarry.y)
                nextCarry
            }
            minX = localMinX
            maxX = localMaxX
            minY = localMinY
            maxY = localMaxY
        }

        val width: Double get() = maxX - minX
        val height: Double get() = maxY - minY
    }

    private fun buildRouteTransform(): AffineTransform {
        val t = AffineTransform()
        t.translate(paddingPx.toDouble(), paddingPx.toDouble())
        t.scale(scale, -scale)
        t.translate(-routeBounds.minX, -routeBounds.maxY)
        return t
    }

    val routeBoundsInRouteCoordinateSpace: Dimension
        get() = Dimension(
            ceil(routeBounds.width).toInt(),
            ceil(routeBounds.height).toInt()
        )

    var baseImageNeedsRepaint = true
    fun invalidateBaseImage() {
        baseImageNeedsRepaint = true
    }

    override fun getPreferredSize(): Dimension {
        return minimumSize
    }

    override fun getMinimumSize(): Dimension {
        return Dimension(
            ceil(routeBounds.width * scale).toInt() + paddingPx * 2,
            ceil(routeBounds.height * scale).toInt() + paddingPx * 2,
        )
    }

    fun fitScaleToSize(targetWidth: Int, targetHeight: Int) {
        val scaleX = (targetWidth.toDouble() - paddingPx * 2) / routeBounds.width
        val scaleY = (targetHeight.toDouble() - paddingPx * 2) / routeBounds.height
        scale = scaleX.coerceAtMost(scaleY)
    }

    private fun computeTrackColor(activeFeature: Feature?): Color = when {
        activeFeature is Feature.Corner -> Color.ORANGE
        else -> Color.BLACK
    }

    override fun paintComponent(g: Graphics) {
        g as Graphics2D
        assureBaseImageIsUpToDate()

        g.drawImage(baseImage, 0, 0, null)

        g.color = computeTrackColor(null)
        g.drawLine(10, height - 10, 10 + (100.0 * scale).toInt(), height - 10)
        g.font = g.font.deriveFont(Font.PLAIN, 14.0f)
        g.drawString("100m", 10, height - 10 - (lineThickness * 2.0f).toInt())

        paintCarMarker(g)

        if (hoveredInspectable != null) {
            g.color = Color(0x8020FF00.toInt(), true)
            g.fill(hoveredInspectable!!.displayShape)
        }
    }

    override fun paintChildren(g: Graphics?) {
        super.paintChildren(g)
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

    private lateinit var baseImage: BufferedImage
    private val inspectables = ArrayList<Inspectable>()
    private fun assureBaseImageIsUpToDate() {
        val targetWidth = ceil(routeBounds.width * scale).toInt() + paddingPx * 2
        val targetHeight = ceil(routeBounds.height * scale).toInt() + paddingPx * 2
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
        inspectables.clear()

        val startFinishMarkerRadius = routeBounds.width.coerceAtLeast(routeBounds.height) / 25.0 / 3.0

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
        var activeFeature: Feature? = null
        val currentFeaturePoints = ArrayList<Pair<Double, Double>>()
        for (segment in route) {
            carryPoint += segment.forward

            val x = carryPoint.x
            val y = carryPoint.y

            val nextActiveFeature = features.find { distanceCarry in it.startsAtTrackDistance..(it.startsAtTrackDistance + it.length) }
            if (nextActiveFeature !== activeFeature) {
                if (activeFeature != null) {
                    val featurePxPoints = currentFeaturePoints.map { (fx, fy) ->
                        val pt = routeTransform.transform(Point2D.Double(fx, fy), null)
                        Pair(pt.x, pt.y)
                    }
                    inspectables.add(Inspectable(
                        featurePointsToShape(featurePxPoints, FEATURE_HOVER_SHAPE_THICKNESS_PX),
                        featurePointsToShape(featurePxPoints, FEATURE_DISPLAY_SHAPE_THICKNESS_PX),
                        activeFeature
                    ))
                }
                currentFeaturePoints.clear()
                if (nextActiveFeature != null) {
                    currentFeaturePoints.add(Pair(prevX, prevY))
                }
            }
            activeFeature = nextActiveFeature
            if (activeFeature != null) {
                currentFeaturePoints.add(Pair(x, y))
            }

            val lineLength = Vector3(prevX - x, prevY - y, 0.0).length2d
            val drawThisLine = lineLength * scale > (lineThickness * 1.75)
            if (drawThisLine) {
                g.color = computeTrackColor(activeFeature)
                g.draw(Line2D.Double(prevX, prevY, x, y))
            }

            if (segmentJointMarkerColor != null) {
                val jointMarkerRadius = (lineThickness + 1) / (2.0 * scale)
                g.color = segmentJointMarkerColor
                g.fill(Ellipse2D.Double(prevX - jointMarkerRadius, prevY - jointMarkerRadius, jointMarkerRadius * 2, jointMarkerRadius * 2))
            }
            distanceCarry += segment.length
            distanceSinceLastMarker += segment.length
            if (distanceSinceLastMarker >= distanceMarkersEvery && distanceMarkerColor != null) {
                distanceSinceLastMarker = 0.meters
                val labelPt = routeTransform.transform(Point2D.Double(x, y), null)
                withTransform(g, AffineTransform()) {
                    g.color = distanceMarkerColor
                    g.drawString(distanceToString(distanceCarry), (labelPt.x + 30).toInt(), (labelPt.y + 10).toInt())
                }
            }

            if (drawThisLine) {
                prevX = x
                prevY = y
            }
        }

        g.color = startMarkerColor
        g.draw(Ellipse2D.Double(-startFinishMarkerRadius, -startFinishMarkerRadius, startFinishMarkerRadius * 2, startFinishMarkerRadius * 2))

        g.color = finishMarkerColor
        g.draw(Ellipse2D.Double(carryPoint.x - startFinishMarkerRadius, carryPoint.y - startFinishMarkerRadius, startFinishMarkerRadius * 2, startFinishMarkerRadius * 2))

        val finishPt = routeTransform.transform(Point2D.Double(prevX, prevY), null)
        withTransform(g, AffineTransform()) {
            g.color = segmentJointMarkerColor
            val distanceText = String.format("%3.2f km", (distanceCarry.toDoubleInMeters() / 1000.0))
            g.drawString(distanceText, finishPt.x.toInt(), (finishPt.y + 10).toInt())
        }

        g.dispose()
        baseImageNeedsRepaint = false
    }

    private fun distanceToString(distance: Distance): String {
        return String.format("%3.2f km", (distance.toDoubleInMeters() / 1000.0))
    }

    private fun featurePointsToShape(points: List<Pair<Double, Double>>, thicknessPx: Double): Shape {
        val pointsOnRouteWithPerpendiculars = points
            .asSequence()
            .windowed(size = 2, step = 1, partialWindows = false)
            .filter { (p1, p2) ->
                p1 != p2
            }
            .withIndex()
            .map { (index, points) ->
                val (x1, y1) = points[0]
                val (x2, y2) = points[1]
                val vecToP1 = Vector3(x1, y1, 0.0)
                val vecP1P2 = Vector3(x2 - vecToP1.x, y2 - vecToP1.y, 0.0)
                val perpendicularVec = vecP1P2.rotate2d90degCounterClockwise().withLength2d(thicknessPx)
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

    private var hoveredInspectable: Inspectable? = null

    private inner class Inspectable(
        val hoverShape: Shape,
        val displayShape: Shape,
        val feature: Feature,
    ) {
        private val toolTip: JToolTip by lazy {
            val text = StringBuilder()
            text.append("<html>")
            when (feature) {
                is Feature.Straight -> {
                    text.append("d=")
                    text.append(feature.length.toDoubleInMeters().roundToInt())
                    text.append("m")
                    text.append("<br>")
                    text.append("∠=")
                    text.append(feature.angleFirstToLast.toDoubleInDegrees().roundToInt())
                    text.append("°")
                }

                is Feature.Corner -> {
                    text.append("Ør=")
                    text.append(feature.segments.compoundRadius)
                    text.append("<br>")
                    text.append("∠=")
                    text.append(feature.totalAngle)
                    text.append("<br>")
                    text.append("d=")
                    text.append(feature.length.toDoubleInMeters().roundToInt())
                    text.append("m<br>")
                    val pacenote = cornerFeatureToPacenoteItem(feature)
                    text.append(pacenote.toString())
                }
            }
            text.append("<br>")
            text.append("@")
            text.append(distanceToString(feature.startsAtTrackDistance))
            text.append("</html>")
            val shapeBounds = displayShape.bounds

            JToolTip().apply {
                isOpaque = true
                isVisible = true
                tipText = text.toString()
                size = preferredSize
                location = Point(shapeBounds.x + shapeBounds.width / 2, shapeBounds.y + shapeBounds.height / 2 - height)
                while (displayShape.intersects(bounds)) {
                    location = Point(location.x + 10, location.y + 10)
                }
            }
        }

        fun onHoverEnter(e: MouseEvent) {
            this@RouteComponent.add(toolTip)
            revalidate()
        }

        fun onHoverLeave(e: MouseEvent) {
            this@RouteComponent.remove(toolTip)
        }
    }

    init {
        addMouseMotionListener(object : MouseMotionListener {
            override fun mouseMoved(e: MouseEvent) {
                val inspectable = inspectables.find { it.hoverShape.contains(e.point) }
                if (inspectable === hoveredInspectable) {
                    return
                }
                hoveredInspectable?.onHoverLeave(e)
                inspectable?.onHoverEnter(e)
                hoveredInspectable = inspectable
                repaint()
            }

            override fun mouseDragged(e: MouseEvent?) {}
        })
    }

    companion object {
        const val FEATURE_HOVER_SHAPE_THICKNESS_PX = 20.0
        const val FEATURE_DISPLAY_SHAPE_THICKNESS_PX = 6.0
    }
}

private inline fun withTransform(g: Graphics2D, transform: AffineTransform, crossinline block: () -> Unit) {
    val saved = g.transform
    g.transform = transform
    try {
        block()
    } finally {
        g.transform = saved
    }
}