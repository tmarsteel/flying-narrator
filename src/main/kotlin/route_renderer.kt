package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.DropLastSequence.Companion.dropLast
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.stream.IntStream
import kotlin.math.ceil
import kotlin.math.floor

fun Route.render(
    scale: Double = 0.4,
    distanceMarkersEveryMeters: Double = 200.0,
    distanceMarkerColor: Color = Color.RED,
    paddingPxs: Int = 50,
    bgColor: Color = Color.WHITE,
    trackColor: Color = Color.BLACK,
    segmentJointMarkerColor: Color? = Color.RED,
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
    val startFinishMarkerRadius = width.coerceAtLeast(height) / 25.0 / 3.0

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
        trackToImageX(0 - startFinishMarkerRadius),
        trackToImageY(0 + startFinishMarkerRadius),
        (startFinishMarkerRadius * 2 * scale).toInt(),
        (startFinishMarkerRadius * 2 * scale).toInt()
    )

    g.color = finishMarkerColor
    g.drawOval(
        trackToImageX(carryPoint.x - startFinishMarkerRadius),
        trackToImageY(carryPoint.y + startFinishMarkerRadius),
        (startFinishMarkerRadius * 2 * scale).toInt(),
        (startFinishMarkerRadius * 2 * scale).toInt()
    )
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

fun Sequence<TrackSegment>.toGeogebraSyntax(): String {
    fun pointName(index: Int) = index
        // avoid using X, Y and Z because reserved in geogebra
        .toString(23)
        .chars()
        .map {
            when {
                it in 48..57 -> it + 17
                else -> it - 32 + 10
            }
        }
        .collectCodePointsToString()
    fun vecName(index: Int) = pointName(index).lowercase()

    val sb = StringBuilder()
    sb.appendLine(
        """
        ggbApplet.getAllObjectNames().forEach(o => ggbApplet.deleteObject(o));
        ggbApplet.evalCommand("${pointName(0)}=Point({0,0})");
        ggbApplet.setLayer("${pointName(0)}", 1);
    """.trimIndent()
    )
    this.forEachIndexed { index, vec ->
        val prevPointName = pointName(index)
        val pointName = pointName(index + 1)
        val vecName = vecName(index)
        sb.appendLine(
            """
            ggbApplet.evalCommand("$pointName=$prevPointName+Vector((${vec.roadSegment.x},${vec.roadSegment.y}))");
            ggbApplet.evalCommand("$vecName=Vector($prevPointName,$pointName)");
            ggbApplet.setLayer("$pointName", 1);
            ggbApplet.setLayer("$vecName", 0);
        """.trimIndent()
        )
    }

    fun lotLineName(index: Int) = "lot${pointName(index)}"
    for (index in 1 until this.count()) {
        val prevPointName = pointName(index - 1)
        val pointName = pointName(index)
        val nextPointName = pointName(index + 1)
        val lotLineName = lotLineName(index)
        sb.appendLine(
            """
            ggbApplet.evalCommand("$lotLineName=Line($pointName, AngleBisector($prevPointName, $pointName, $nextPointName))");
            ggbApplet.setLayer("$lotLineName", 1);
        """.trimIndent()
        )
    }

    fun centerPointName(index: Int) = "c${pointName(index)}"
    for (index in 1 until this.count() - 1) {
        val lotLineName = lotLineName(index)
        val nextLotLineName = lotLineName(index + 1)
        val centerPointName = centerPointName(index)
        sb.appendLine(
            """
            ggbApplet.evalCommand("$centerPointName=Intersect($lotLineName, $nextLotLineName)");
            ggbApplet.setLayer("$centerPointName", 1);
        """.trimIndent()
        )
    }

    fun arcName(index: Int) = "a${pointName(index)}"
    fun radiusName(index: Int) = "r${arcName(index)}"
    for ((index, segment) in this.withIndex().drop(1).dropLast(1)) {
        val centerPointName = centerPointName(index)
        val pointName = pointName(index)
        val nextPointName = pointName(index + 1)
        val arcName = arcName(index)
        val radiusName = radiusName(index)
        if (segment.angleToNext < 0.0) {
            sb.appendLine(
                """
                ggbApplet.evalCommand("$arcName=CircularArc($centerPointName, $pointName, $nextPointName)");
            """.trimIndent()
            )
        } else {
            sb.appendLine(
                """
                ggbApplet.evalCommand("$arcName=CircularArc($centerPointName, $nextPointName, $pointName)");
            """.trimIndent()
            )
        }
        sb.appendLine(
            """
            ggbApplet.evalCommand("$radiusName=Radius($arcName)");
            ggbApplet.setLayer("$arcName", 2);
            ggbApplet.setLayer("$radiusName", 2);
        """.trimIndent()
        )
    }

    sb.appendLine(
        """
        ggbApplet.setLayerVisible(0, true);
        ggbApplet.setLayerVisible(1, false);
        ggbApplet.setLayerVisible(2, true);
    """.trimIndent()
    );

    return sb.toString()
}

private fun IntStream.collectCodePointsToString(): String {
    return collect(
        { StringBuilder() },
        { sb, cp -> sb.appendCodePoint(cp) },
        { sb1, sb2 -> sb1.append(sb2) },
    ).toString()
}