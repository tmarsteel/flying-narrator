package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.Vector3
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import io.github.tmarsteel.flyingnarrator.foldInto
import io.github.tmarsteel.flyingnarrator.pacenote.inferred.cornerFeatureToPacenoteItem
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
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JToolTip
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class RouteComponent(
    val route: Route,
    val features: List<Feature>,
) : JComponent() {
    var scale by RepaintBaseImageOnChange(0.4, alsoOnChange = { revalidate() })
    var distanceMarkersEveryMeters by RepaintBaseImageOnChange(200.0)
    var distanceMarkerColor: Color? by RepaintBaseImageOnChange(Color.RED)
    var startMarkerColor by RepaintBaseImageOnChange(Color.RED)
    var finishMarkerColor by RepaintBaseImageOnChange(Color.GREEN)
    var lineThickness by RepaintBaseImageOnChange(3.0f)
    var segmentJointMarkerColor: Color? by RepaintBaseImageOnChange(null)
    var paddingPx by RepaintBaseImageOnChange(100, alsoOnChange = { revalidate() })

    private val routeCoordinateSystem = object {
        private val offsetX: Double
        private val offsetY: Double
        val width: Double
        val height: Double

        init {
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

            offsetX = -minX
            offsetY = -minY
            width = maxX - minX
            height = maxY - minY
        }

        val baseImageTargetWidthPx: Int get() = ceil(width * scale).toInt() + paddingPx * 2
        val baseImageTargetHeightPx: Int get() = ceil(height * scale).toInt() + paddingPx * 2
        fun routeToBaseImageX(trackX: Double) = ceil((trackX + offsetX) * scale).toInt() + paddingPx
        fun routeToBaseImageY(trackY: Double) = ceil((height - (trackY + offsetY)) * scale).toInt() + paddingPx
        fun baseImageXToRouteX(imageX: Int) = (imageX - paddingPx).toDouble() / scale - offsetX
        fun baseImageYToRouteY(imageY: Int) = -(((imageY - paddingPx).toDouble() / scale) - height + offsetY)
    }

    val routeBoundsInRouteCoordinateSpace: Dimension
        get() = Dimension(
            ceil(routeCoordinateSystem.width).toInt(),
            ceil(routeCoordinateSystem.height).toInt()
        )

    var baseImageNeedsRepaint = true
    fun invalidateBaseImage() {
        baseImageNeedsRepaint = true
    }

    override fun getPreferredSize(): Dimension {
        return minimumSize
    }

    override fun getMinimumSize(): Dimension {
        return Dimension(routeCoordinateSystem.baseImageTargetWidthPx, routeCoordinateSystem.baseImageTargetHeightPx)
    }

    fun fitScaleToSize(targetWidth: Int, targetHeight: Int) {
        val scaleX = (targetWidth.toDouble() - paddingPx * 2) / routeCoordinateSystem.width
        val scaleY = (targetHeight.toDouble() - paddingPx * 2) / routeCoordinateSystem.height
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

    var carPositionOnTrack: Double = -1.0
        set(value) {
            field = value
            updateCarMarkerState()
            repaint()
        }
    private var carMarkerPositionInTrackCoords: Vector3 = Vector3.ORIGIN
    private var carMarkerOrientation: Double = 0.0
    private fun updateCarMarkerState() {
        if (carPositionOnTrack < 0.0) {
            return
        }
        var distanceCarry = 0.0
        var positionCarry = Vector3.ORIGIN
        for (segment in route) {
            val nextDistanceCarry = distanceCarry + segment.length
            if (carPositionOnTrack in distanceCarry..nextDistanceCarry) {
                carMarkerPositionInTrackCoords = positionCarry + segment.forward.withLength(carPositionOnTrack - distanceCarry)
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
        if (carPositionOnTrack < 0.0) {
            return
        }

        val carMarkerG = g.create() as Graphics2D
        carMarkerG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        try {
            carMarkerG.translate(
                routeCoordinateSystem.routeToBaseImageX(carMarkerPositionInTrackCoords.x),
                routeCoordinateSystem.routeToBaseImageY(carMarkerPositionInTrackCoords.y),
            )
            carMarkerG.rotate(carMarkerOrientation)
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
        if (!baseImageNeedsRepaint && this::baseImage.isInitialized) {
            return
        }
        if (!this::baseImage.isInitialized || this.baseImage.width != routeCoordinateSystem.baseImageTargetWidthPx || this.baseImage.height != routeCoordinateSystem.baseImageTargetHeightPx) {
            baseImage = BufferedImage(
                routeCoordinateSystem.baseImageTargetWidthPx.coerceAtLeast(1),
                routeCoordinateSystem.baseImageTargetHeightPx.coerceAtLeast(1),
                BufferedImage.TYPE_INT_RGB
            )
        }

        val bgColor = Color.WHITE
        inspectables.clear()

        val startFinishMarkerRadius =
            routeCoordinateSystem.width.coerceAtLeast(routeCoordinateSystem.height) / 25.0 / 3.0

        val g = baseImage.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.stroke = BasicStroke(lineThickness)
        g.color = bgColor
        g.fillRect(0, 0, baseImage.width, baseImage.height)

        var carryPoint = Vector3.ORIGIN
        var prevImageX = routeCoordinateSystem.routeToBaseImageX(carryPoint.x)
        var prevImageY = routeCoordinateSystem.routeToBaseImageY(carryPoint.y)
        var distanceCarry = 0.0
        var distanceSinceLastMarker = 0.0
        var activeFeature: Feature? = null
        val currentFeaturePoints = ArrayList<Pair<Int, Int>>()
        for (segment in route) {
            carryPoint += segment.forward

            val imageX = routeCoordinateSystem.routeToBaseImageX(carryPoint.x)
            val imageY = routeCoordinateSystem.routeToBaseImageY(carryPoint.y)

            val nextActiveFeature = features.find { distanceCarry in it.startsAtTrackDistance..(it.startsAtTrackDistance + it.length) }
            if (nextActiveFeature !== activeFeature) {
                if (activeFeature != null) {
                    inspectables.add(Inspectable(
                        featurePointsToShape(currentFeaturePoints, FEATURE_HOVER_SHAPE_THICKNESS_PX),
                        featurePointsToShape(currentFeaturePoints, FEATURE_DISPLAY_SHAPE_THICKNESS_PX),
                        activeFeature
                    ))
                }
                currentFeaturePoints.clear()
                if (nextActiveFeature != null) {
                    currentFeaturePoints.add(Pair(prevImageX, prevImageY))
                }
            }
            activeFeature = nextActiveFeature
            if (activeFeature != null) {
                currentFeaturePoints.add(Pair(imageX, imageY))
            }

            val lineLength = Vector3(prevImageX.toDouble() - imageX.toDouble(), prevImageY.toDouble() - imageY.toDouble(), 0.0).length2d
            val drawThisLine = lineLength > (lineThickness * 1.75)
            if (drawThisLine) {
                g.color = computeTrackColor(activeFeature)
                g.drawLine(prevImageX, prevImageY, imageX, imageY)
            }

            if (segmentJointMarkerColor != null) {
                g.color = segmentJointMarkerColor
                g.fillOval(
                    floor(prevImageX - (lineThickness + 1) / 2).toInt(),
                    floor(prevImageY - (lineThickness + 1) / 2).toInt(),
                    ceil(lineThickness + 1).toInt(),
                    ceil(lineThickness + 1).toInt(),
                )
            }
            distanceCarry += segment.length
            distanceSinceLastMarker += segment.length
            if (distanceSinceLastMarker >= distanceMarkersEveryMeters && distanceMarkerColor != null) {
                distanceSinceLastMarker = 0.0
                g.color = distanceMarkerColor
                g.drawString(distanceToString(distanceCarry), imageX + 30, imageY + 10)
            }

            if (drawThisLine) {
                prevImageX = imageX
                prevImageY = imageY
            }
        }

        g.color = startMarkerColor
        g.drawOval(
            routeCoordinateSystem.routeToBaseImageX(0 - startFinishMarkerRadius),
            routeCoordinateSystem.routeToBaseImageY(0 + startFinishMarkerRadius),
            (startFinishMarkerRadius * 2 * scale).toInt(),
            (startFinishMarkerRadius * 2 * scale).toInt()
        )

        g.color = finishMarkerColor
        g.drawOval(
            routeCoordinateSystem.routeToBaseImageX(carryPoint.x - startFinishMarkerRadius),
            routeCoordinateSystem.routeToBaseImageY(carryPoint.y + startFinishMarkerRadius),
            (startFinishMarkerRadius * 2 * scale).toInt(),
            (startFinishMarkerRadius * 2 * scale).toInt()
        )
        g.color = segmentJointMarkerColor
        val distanceText = String.format("%3.2f km", (distanceCarry / 1000.0))
        g.drawString(distanceText, prevImageX, prevImageY + 10)

        g.dispose()
        baseImageNeedsRepaint = false
    }

    private fun distanceToString(distance: Double): String {
        return String.format("%3.2f km", (distance / 1000.0))
    }

    private fun featurePointsToShape(points: List<Pair<Int, Int>>, thicknessPx: Double): Shape {
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
                val vecToP1 = Vector3(x1.toDouble(), y1.toDouble(), 0.0)
                val vecP1P2 = Vector3(x2.toDouble() - vecToP1.x, y2.toDouble() - vecToP1.y, 0.0)
                val perpendicularVec = vecP1P2.rotate2d90degCounterClockwise().withLength2d(thicknessPx)
                val endPair = Pair(Vector3(x2.toDouble(), y2.toDouble(), 0.0), perpendicularVec)
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
        return Vector3(
            routeCoordinateSystem.baseImageXToRouteX(componentPosition.x),
            routeCoordinateSystem.baseImageYToRouteY(componentPosition.y),
            0.0,
        )
    }

    fun getComponentPositionFromRoutePosition(routePosition: Vector3): Point {
        return Point(
            routeCoordinateSystem.routeToBaseImageX(routePosition.x),
            routeCoordinateSystem.routeToBaseImageY(routePosition.y),
        )
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
                    text.append(feature.length.toInt())
                    text.append("m")
                    text.append("<br>")
                    text.append("∠=")
                    text.append(Math.toDegrees(feature.angleFirstToLast).roundToInt())
                    text.append("°")
                }

                is Feature.Corner -> {
                    text.append("Ør=")
                    text.append(feature.segments.compoundRadius.roundToInt())
                    text.append("m<br>")
                    text.append("∠=")
                    text.append(Math.toDegrees(feature.totalAngle).roundToInt())
                    text.append("°<br>")
                    text.append("d=")
                    text.append(feature.length.toInt())
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