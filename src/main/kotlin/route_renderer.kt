package io.github.tmarsteel.flyingnarrator

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.text.NumberFormat
import kotlin.math.ceil
import kotlin.math.floor

fun Route.render(
    scale: Double = 0.4,
    distanceMarkersEveryMeters: Double = 200.0,
    paddingPxs: Int = 50,
    bgColor: Color = Color.WHITE,
    trackColor: Color = Color.BLACK,
    segmentJointMarkerColor: Color = Color.RED,
    startMarkerColor: Color = Color.RED,
    finishMarkerColor: Color = Color.GREEN,
    lineThickness: Float = 3.0f,
): BufferedImage {
    var minX = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    fold(Vector3.ORIGIN) { carryPt, vec ->
        val nextCarry = carryPt + vec
        minX = minX.coerceAtMost(nextCarry.x)
        maxX = maxX.coerceAtLeast(nextCarry.x)
        minY = minY.coerceAtMost(nextCarry.y)
        maxY = maxY.coerceAtLeast(nextCarry.y)
        nextCarry
    }

    val offsetX = -minX
    val offsetY = -minY
    val width = maxX - minX
    val height = maxY - minY
    val markerRadius = width.coerceAtLeast(height) / 25.0 / 3.0

    val image = BufferedImage(
        (width * scale).toInt() + paddingPxs * 2,
        (height * scale).toInt() + paddingPxs * 2,
        BufferedImage.TYPE_INT_RGB,
    )

    fun trackToImageX(v: Double) = ceil((v + offsetX) * scale).toInt() + paddingPxs
    fun trackToImageY(v: Double) = ceil((height - (v + offsetY)) * scale).toInt() + paddingPxs

    val g = image.graphics as Graphics2D
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.stroke = BasicStroke(lineThickness)
    g.color = bgColor
    g.fillRect(0, 0, image.width, image.height)

    g.color = trackColor
    var carryPoint = Vector3.ORIGIN
    var prevImageX = trackToImageX(carryPoint.x)
    var prevImageY = trackToImageY(carryPoint.y)
    var distanceCarry = 0.0
    var distanceSinceLastMarker = 0.0
    for (vec in this) {
        carryPoint += vec

        val imageX = trackToImageX(carryPoint.x)
        val imageY = trackToImageY(carryPoint.y)
        g.color = trackColor
        g.drawLine(prevImageX, prevImageY, imageX, imageY)

        g.color = segmentJointMarkerColor
        g.fillOval(
            floor(prevImageX - (lineThickness + 1) / 2).toInt(),
            floor(prevImageY - (lineThickness + 1) / 2).toInt(),
            ceil(lineThickness + 1).toInt(),
            ceil(lineThickness + 1).toInt(),
        )
        distanceCarry += vec.length()
        distanceSinceLastMarker += vec.length()
        if (distanceSinceLastMarker >= distanceMarkersEveryMeters) {
            distanceSinceLastMarker = 0.0
            val distanceText = String.format("  %3.2f km", (distanceCarry / 1000.0))
            g.drawString(distanceText, imageX, imageY + 10)
        }
        prevImageX = imageX
        prevImageY = imageY
    }

    g.color = startMarkerColor
    g.drawOval(trackToImageX(0 - markerRadius), trackToImageY(0 - markerRadius), (markerRadius * 2 * scale).toInt(), (markerRadius * 2 * scale).toInt())

    g.color = finishMarkerColor
    g.drawOval(trackToImageX(carryPoint.x - markerRadius), trackToImageY(carryPoint.y - markerRadius), (markerRadius * 2 * scale).toInt(), (markerRadius * 2 * scale).toInt())
    g.color = segmentJointMarkerColor
    val distanceText = String.format("%3.2f km", (distanceCarry / 1000.0))
    g.drawString(distanceText, prevImageX, prevImageY + 10)

    g.color = trackColor
    g.drawLine(10, image.height - 10, 10 + (100.0 * scale).toInt(), image.height - 10)
    g.font = g.font.deriveFont(Font.PLAIN, 14.0f)
    g.drawString("100m", 10, image.height - 10 - (lineThickness * 2.0f).toInt())

    g.dispose()
    return image
}