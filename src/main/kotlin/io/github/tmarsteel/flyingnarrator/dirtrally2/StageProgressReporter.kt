package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.StageProgressReporter.Companion.OPTIMAL_SAMPLING_INTERVAL
import io.github.tmarsteel.flyingnarrator.dirtrally2.StageProgressReporter.Companion.PROGRESS_INDICATOR_HEIGHT_PROPORTION
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds


/**
 * Analyzes live screen captures while playing (or screenshots) for the on-screen progress indicator
 * in the game and deduces progress through the stage.
 *
 * You should feed game frames to [getStageProgressFromGameFrame] every [OPTIMAL_SAMPLING_INTERVAL] for the highest
 * resolution.
 *
 * This will provide around [PROGRESS_INDICATOR_HEIGHT_PROPORTION] discrete progress values per vertical pixel
 * of screen resolution; so the accuracy of this is `stageLength / screenHeight * PROGRESS_INDICATOR_HEIGHT_PROPORTION`
 * meters. Some examples:
 *
 * |stage length |screen height|accuracy|
 * |-------------|-------------|--------|
 * |5km          |1440px       | 4.8m   |
 * |30km         |1440px       |29.0m   |
 * |5km          |1080px       | 6.5m   |
 * |30km         |1080px       |38.7m   |
 * |5km          | 720px       | 9.6m   |
 * |30km         | 720px       |58.1m   |
 *
 * **Keeping a single instance of this class through a stage run or game session will speed it up
 * by re-using memory between frames.**
 * The measured time to analyze a single 2560x1440 frame on a Ryzen 5 5600 was ~150µs, so even on slow
 * processors very quick compared to [OPTIMAL_SAMPLING_INTERVAL].
 */
class StageProgressReporter {
    /**
     * @param width the width of a full game frame
     * @param height the height of a full game frame
     * @return the area that contains the progress indicator, so you can pass exactly that data to [getProgressFromProgressIndicatorInGameFrame]
     * (see [BufferedImage.getSubimage])
     */
    fun getCropAreaForFrameSize(width: Int, height: Int): Rectangle {
        return Rectangle(
            ceil(width.toDouble() * PROGRESS_INDICATOR_OFFSET_X_PROPORTION).toInt(),
            ceil(height.toDouble() * PROGRESS_INDICATOR_OFFSET_Y_PROPORTION).toInt(),
            floor(width.toDouble() * PROGRESS_INDICATOR_WIDTH_PROPORTION).toInt(),
            floor(height.toDouble() * PROGRESS_INDICATOR_HEIGHT_PROPORTION).toInt(),
        )
    }

    private lateinit var progressIndicatorPixelBuffer: IntArray
    private lateinit var playerIndicatorPixelBuffer: IntArray
    private lateinit var playerIndicatorSize: Dimension

    /**
     * @param indicatorImage a game frame cropped exactly to the indicator
     * @return progress through the stage between `0.0` and `1.0`, or `-1.0` if the progress cannot
     *         be determined reliably.
     */
    fun getProgressFromProgressIndicatorInGameFrame(indicatorImage: BufferedImage): Double {
        val progressIndicatorPixelBufferSize = indicatorImage.width * indicatorImage.height
        if (!::progressIndicatorPixelBuffer.isInitialized || progressIndicatorPixelBuffer.size != progressIndicatorPixelBufferSize) {
            progressIndicatorPixelBuffer = IntArray(progressIndicatorPixelBufferSize)
        }
        indicatorImage.getRGB(0, 0, indicatorImage.width, indicatorImage.height, progressIndicatorPixelBuffer, 0, indicatorImage.width)

        if (!::playerIndicatorPixelBuffer.isInitialized || playerIndicatorSize.width != indicatorImage.width) {
            val playerIndicatorImage = renderPlayerIndicator(indicatorImage.width)
            playerIndicatorPixelBuffer = IntArray(playerIndicatorImage.width * playerIndicatorImage.height)
            playerIndicatorImage.getRGB(0, 0, playerIndicatorImage.width, playerIndicatorImage.height, playerIndicatorPixelBuffer, 0, playerIndicatorImage.width)
            playerIndicatorSize = Dimension(playerIndicatorImage.width, playerIndicatorImage.height)
        }
        check(playerIndicatorSize.width <= indicatorImage.width)

        var bestMatch = 0.0
        var bestMatchAtY = 0
        for (indicatorImageY in indicatorImage.height - playerIndicatorSize.height downTo 0) {
            val matchQuality = evaluateImageMatch(
                haystackImagePixels = progressIndicatorPixelBuffer,
                haystackPosX = 0,
                haystackPosY = indicatorImageY,
                haystackWidth = indicatorImage.width,
                needlePixels = playerIndicatorPixelBuffer,
                needleWidth = playerIndicatorSize.width,
                needleHeight = playerIndicatorSize.height,
            )
            if (matchQuality > bestMatch) {
                bestMatch = matchQuality
                bestMatchAtY = indicatorImageY
            }
        }

        if (bestMatch < PLAYER_INDICATOR_MIN_MATCH_QUALITY) {
            return -1.0
        }

        val progress = 1.0 - bestMatchAtY.toDouble() / (indicatorImage.height - playerIndicatorSize.height).toDouble()
        return progress
    }

    companion object {
        /**
         * this is a bit faster than the resolution provided by the games progress
         * indicator. It will sometimes result in two consecutive calls to [getStageProgressFromGameFrame] will
         * report the same number.
         */
        val OPTIMAL_SAMPLING_INTERVAL = 100.milliseconds

        /*
        At 2560x1440, the indicator starts at 107x334 and occupies 8x1007 pixels
        The maximum measured height for the indicator was 6px
         */
        const val PROGRESS_INDICATOR_OFFSET_X_PROPORTION = 0.0390625
        const val PROGRESS_INDICATOR_WIDTH_PROPORTION = 0.00859375
        const val PROGRESS_INDICATOR_OFFSET_Y_PROPORTION = 0.2208333333
        const val PROGRESS_INDICATOR_HEIGHT_PROPORTION = 0.71875

        /**
         * the player indicator is considered to be found with confidence when [evaluateImageMatch]
         * returns a value of this or greater
         */
        const val PLAYER_INDICATOR_MIN_MATCH_QUALITY = 0.8
    }
}

private fun evaluateImageMatch(
    haystackImagePixels: IntArray,
    haystackPosX: Int,
    haystackPosY: Int,
    haystackWidth: Int,
    needlePixels: IntArray,
    needleWidth: Int,
    needleHeight: Int,
): Double {
    var totalDifference = 0.0
    var nSignificantPixels = 0
    for (needleX in 0 until needleWidth) {
        for (needleY in 0 until needleHeight) {
            val needlePixel = needlePixels[needleY * needleWidth + needleX]
            val needlePixelAlpha = needlePixel.toUInt() shr 24
            if (needlePixelAlpha != 255u) {
                continue
            }
            val haystackPixel = haystackImagePixels[(haystackPosY + needleY) * haystackWidth + haystackPosX + needleX]
            val pixelDistance = colorDistance(needlePixel, haystackPixel)
            totalDifference += pixelDistance
            nSignificantPixels++
        }
    }

    return 1 - (totalDifference / nSignificantPixels.toDouble())
}

/**
 * @param color1 RGB, alpha is ignored
 * @param color2 RGB, alpha is ignored
 * @return distance between the two colors, component wise: `0.0` = identical colors, `1.0` = complement colors
 */
private fun colorDistance(color1: Int, color2: Int): Double {
    // https://en.wikipedia.org/wiki/Color_difference
    val r1 = (color1 shr 16) and 0xFF
    val g1 = (color1 shr 8) and 0xFF
    val b1 = color1 and 0xFF
    val r2 = (color2 shr 16) and 0xFF
    val g2 = (color2 shr 8) and 0xFF
    val b2 = color2 and 0xFF

    val dR = (r1 - r2).toDouble()
    val dG = (g1 - g2).toDouble()
    val dB = (b1 - b2).toDouble()
    val invR = (r1 + r2) / 2.0
    val delta = sqrt(
        (2.0 + invR / 256.0) * dR.pow(2.0) +
        4.0 * dG.pow(2.0) +
        (2.0 + (255.0 - invR) / 256.0) * dB.pow(2.0)
    )

    return delta.absoluteValue / 764.8339663572415
}

/**
 * Renders an image that is very similar to the the 45°-tilted square that indicates the progress through the stage to
 * the left of the screen.
 * @return the indicator, as a RGBA image
 */
private fun renderPlayerIndicator(width: Int): BufferedImage {
    // choose borderWidth and cWidth so that borderWidth/2 AND sqrt(2)*cWidth-borderWidth/2 are both as close
    // to being integer as possible
    val borderWidth = 4
    val cWidth = 29
    val inset = borderWidth / 2
    val center = cWidth / 2
    val shape = Polygon(intArrayOf(center, cWidth-inset, center, 0), intArrayOf(0, center, cWidth-inset, center), 4)

    val image = BufferedImage(width, width, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    // adjust to the actual width we're rendering for to avoid math with the coordinates below
    g.translate(1, 0)
    ((width - 1).toDouble() / cWidth.toDouble()).let {
        g.scale(it, it)
    }

    g.color = Color(0xFF003E)
    g.fill(shape)

    g.stroke = BasicStroke(borderWidth.toFloat() * 1.2f)
    g.color = Color.BLACK.withAlpha(200)
    /*g.clip = shape
    g.drawDropShadowOf(shape, borderWidth)
    g.clip = null*/
    g.stroke = BasicStroke(borderWidth.toFloat() * 0.8f)
    g.color = Color.WHITE
    g.draw(shape)

    g.dispose()

    return image
}

private fun Color.withAlpha(alpha: Int): Color {
    return Color(red, green, blue, alpha)
}

private fun Graphics2D.drawDropShadowOf(
    shape: Shape,
    shadowBlurRadius: Int,
) {
    // Bounding Box + Blur-Rand
    val bounds = shape.bounds
    val shadowWidth = bounds.width + shadowBlurRadius * 4
    val shadowHeight = bounds.height + shadowBlurRadius * 4

    val sharpShadowImage = BufferedImage(shadowWidth, shadowHeight, BufferedImage.TYPE_INT_ARGB)
    val g = sharpShadowImage.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.translate(-bounds.x + shadowBlurRadius * 2, -bounds.y + shadowBlurRadius * 2)
    g.stroke = this.stroke
    g.color = this.color
    g.draw(shape)
    g.dispose()

    val op = ConvolveOp(
        makeGaussianBlurKernel(shadowBlurRadius),
        ConvolveOp.EDGE_NO_OP,
        null
    )
    val blurredShadowImage = op.filter(sharpShadowImage, null)

    this.drawImage(blurredShadowImage, bounds.x - shadowBlurRadius * 2, bounds.y - shadowBlurRadius * 2, null)
}

private fun makeGaussianBlurKernel(radius: Int): Kernel {
    val size = radius * 2 + 1
    val kernel = FloatArray(size * size)
    val sigma = radius / 3.0f
    var sum = 0f
    for (y in -radius..radius) {
        for (x in -radius..radius) {
            val value = exp((-(x * x + y * y) / (2 * sigma * sigma)).toDouble()).toFloat()
            kernel[(y + radius) * size + (x + radius)] = value
            sum += value
        }
    }

    for (i in kernel.indices) kernel[i] /= sum
    return Kernel(size, size, kernel)
}