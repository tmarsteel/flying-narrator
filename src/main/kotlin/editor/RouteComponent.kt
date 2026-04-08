package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.Feature
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.Vector3
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Point
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.ceil
import kotlin.math.floor

class RouteComponent(
    val route: Route,
    val features: List<Feature>,
) : JComponent() {
    var scale by RepaintBaseImageOnChange(0.4, alsoOnChange = { revalidate() })
    var distanceMarkersEveryMeters by RepaintBaseImageOnChange(200.0)
    var distanceMarkerColor by RepaintBaseImageOnChange(Color.RED)
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
            route.fold(Vector3.ORIGIN) { carryPt, vec ->
                val nextCarry = carryPt + vec
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

    fun fitScaleToSize() {
        val scaleX = (this.width.toDouble() - paddingPx * 2) / routeCoordinateSystem.width
        val scaleY = (this.height.toDouble() - paddingPx * 2) / routeCoordinateSystem.height
        scale = scaleX.coerceAtMost(scaleY)
    }

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

    private fun computeTrackColor(activeFeature: Feature?): Color = when {
        activeFeature is Feature.Corner -> Color.ORANGE
        else -> Color.BLACK
    }

    override fun paintComponent(g: Graphics) {
        assureBaseImageIsUpToDate()

        g.drawImage(baseImage, 0, 0, null)

        g.color = computeTrackColor(null)
        g.drawLine(10, height - 10, 10 + (100.0 * scale).toInt(), height - 10)
        g.font = g.font.deriveFont(Font.PLAIN, 14.0f)
        g.drawString("100m", 10, height - 10 - (lineThickness * 2.0f).toInt())
    }

    private lateinit var baseImage: BufferedImage
    private fun assureBaseImageIsUpToDate() {
        if (!baseImageNeedsRepaint && this::baseImage.isInitialized) {
            return
        }
        if (!this::baseImage.isInitialized || this.baseImage.width != routeCoordinateSystem.baseImageTargetWidthPx || this.baseImage.height != routeCoordinateSystem.baseImageTargetHeightPx) {
            baseImage = BufferedImage(
                routeCoordinateSystem.baseImageTargetWidthPx,
                routeCoordinateSystem.baseImageTargetHeightPx,
                BufferedImage.TYPE_INT_RGB
            )
        }

        val bgColor = Color.WHITE

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
        for (vec in route) {
            carryPoint += vec
            activeFeature =
                features.find { distanceCarry in it.startsAtTrackDistance..(it.startsAtTrackDistance + it.length) }

            val imageX = routeCoordinateSystem.routeToBaseImageX(carryPoint.x)
            val imageY = routeCoordinateSystem.routeToBaseImageY(carryPoint.y)
            g.color = computeTrackColor(activeFeature)
            g.drawLine(prevImageX, prevImageY, imageX, imageY)

            if (segmentJointMarkerColor != null) {
                g.color = segmentJointMarkerColor
                g.fillOval(
                    floor(prevImageX - (lineThickness + 1) / 2).toInt(),
                    floor(prevImageY - (lineThickness + 1) / 2).toInt(),
                    ceil(lineThickness + 1).toInt(),
                    ceil(lineThickness + 1).toInt(),
                )
            }
            distanceCarry += vec.length()
            distanceSinceLastMarker += vec.length()
            if (distanceSinceLastMarker >= distanceMarkersEveryMeters) {
                distanceSinceLastMarker = 0.0
                val distanceText = String.format("  %3.2f km", (distanceCarry / 1000.0))
                g.color = distanceMarkerColor
                g.drawString(distanceText, imageX, imageY + 10)
            }
            prevImageX = imageX
            prevImageY = imageY
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
}