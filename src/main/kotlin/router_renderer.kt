package io.github.tmarsteel.flyingnarrator

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.ceil

fun Route.render(
    scale: Double = 0.4,
    paddingPxs: Int = 50,
    bgColor: Color = Color.WHITE,
    trackColor: Color = Color.BLACK,
    startMarkerColor: Color = Color.RED,
    finishMarkerColor: Color = Color.GREEN,
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
    val markerRadius = width.coerceAtLeast(height) / 25.0 / 2.0

    fun trackToImageX(v: Double) = ceil((v + offsetX) * scale).toInt() + paddingPxs
    fun trackToImageY(v: Double) = ceil((v + offsetY) * scale).toInt() + paddingPxs

    val image = BufferedImage(
        (width * scale).toInt() + paddingPxs * 2,
        (height * scale).toInt() + paddingPxs * 2,
        BufferedImage.TYPE_INT_RGB,
    )
    val g = image.graphics as Graphics2D
    g.stroke = BasicStroke(3.0f)
    g.color = bgColor
    g.fillRect(0, 0, image.width, image.height)

    g.color = trackColor
    var carryPoint = Vector3.ORIGIN
    var prevImageX = trackToImageX(carryPoint.x)
    var prevImageY = trackToImageY(carryPoint.y)
    for (vec in this) {
        carryPoint += vec

        val imageX = trackToImageX(carryPoint.x)
        val imageY = trackToImageY(carryPoint.y)
        g.drawLine(prevImageX, prevImageY, imageX, imageY)
        prevImageX = imageX
        prevImageY = imageY
    }

    g.color = startMarkerColor
    g.drawOval(trackToImageX(0 - markerRadius), trackToImageY(0 - markerRadius), (markerRadius * 2 * scale).toInt(), (markerRadius * 2 * scale).toInt())

    g.color = finishMarkerColor
    g.drawOval(trackToImageX(carryPoint.x - markerRadius), trackToImageY(carryPoint.y - markerRadius), (markerRadius * 2 * scale).toInt(), (markerRadius * 2 * scale).toInt())

    g.rotate(Math.toRadians(90.0))

    g.dispose()
    return image
}