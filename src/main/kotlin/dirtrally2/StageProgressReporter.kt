package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.StageProgressReporter.Companion.OPTIMAL_SAMPLING_INTERVAL
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

/**
 * Analyzes live screen captures while playing (or screenshots) for the on-screen progress indicator
 * in the game and deduces progress through the stage.
 *
 * You should feed game frames to [getStageProgressFromGameFrame] every [OPTIMAL_SAMPLING_INTERVAL] for the highest
 * resolution.
 *
 * This will provide between 0.466 and 1.396 discrete progress values per vertical pixel of screen resolution;
 * so the accuracy is at resolution is like so:
 *
 * | resolution | n discrete steps | delta on a 6km stage | delta on a 35km stage |
 * |------------|------------------|----------------------|-----------------------|
 * | 720x405    | 565              | 10.60m..31.81m       | 61.85m..185.55m       |
 * | 1024x768   | 1073             |  5.59m..16.77m       | 32.62m.. 97.85m       |
 * | 1280x800   | 1117             |  5.36m..16.10m       | 31.31m.. 93.93m       |
 * | 1680x1050  | 1467             |  4.09m..12.23m       | 23.86m.. 71.57m       |
 * | 1920x1200  | 1676             |  3.58m..10.74m       | 20.87m.. 62.62m       |
 * | 2560x1440  | 2011             |  2.98m..8.95m        | 17.40m.. 52.18m       |
 * | 3840x2160  | 3017             |  1.99m..5.95m        | 11.60m.. 34.79m       |
 * | 4096x2304  | 3219             |  1.86m..5.59m        | 10.87m.. 32.61m       |
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

    private lateinit var pixelBuffer: IntArray

    /**
     * @param indicatorImage a game frame cropped exactly to the indicator
     * @return progress through the stage between `0.0` and `1.0`, or `-1.0` if the progress cannot
     *         be determined reliably.
     */
    fun getProgressFromProgressIndicatorInGameFrame(indicatorImage: BufferedImage): Double {
        val pixelBufferSize = indicatorImage.width * indicatorImage.height
        if (!::pixelBuffer.isInitialized || pixelBuffer.size != pixelBufferSize) {
            pixelBuffer = IntArray(pixelBufferSize)
        }
        indicatorImage.getRGB(0, 0, indicatorImage.width, indicatorImage.height, pixelBuffer, 0, indicatorImage.width)

        var startAtY = -1
        var endAtY = 0
        rows@for (row in indicatorImage.height - 1 downTo 0) {
            for (col in 0 until indicatorImage.width) {
                val pixel = pixelBuffer[row * indicatorImage.width + col]
                if (colorDifference(pixel, PLAYER_INDICATOR_COLOR) > PLAYER_INDICATOR_COLOR_TOLERANCE) {
                    if (startAtY != -1) {
                        endAtY = row
                        break@rows
                    } else {
                        continue@rows
                    }
                }
            }

            if (startAtY == -1) {
                // we found the start of the indicator dot
                startAtY = row
            }
        }

        if (startAtY == -1) {
            // found no indicator dot
            return -1.0
        }

        val indicatorHeight = startAtY - endAtY
        if (indicatorHeight.toDouble() / indicatorImage.height.toDouble() > PLAYER_INDICATOR_MAX_HEIGHT_PROPORTION) {
            return -1.0
        }

        if (startAtY == indicatorImage.height - 1) {
            // at the very start of the stage
            return 0.0
        }
        val minProgress = (indicatorImage.height - startAtY).toDouble() / (indicatorImage.height.toDouble() - 1.0)
        val maxProgress = (indicatorImage.height - endAtY).toDouble() / (indicatorImage.height.toDouble() - 1.0)
        return minProgress + (maxProgress - minProgress) / 2.0
    }

    /**
     * @param color1 RGB, alpha is ignored
     * @param color2 RGB, alpha is ignored
     * @return difference of the two colors, component wise: `0.0` = identical colors, `1.0` = complement colors
     */
    private fun colorDifference(color1: Int, color2: Int): Double {
        val r1 = (color1 shr 16) and 0xFF
        val g1 = (color1 shr 8) and 0xFF
        val b1 = color1 and 0xFF
        val r2 = (color2 shr 16) and 0xFF
        val g2 = (color2 shr 8) and 0xFF
        val b2 = color2 and 0xFF
        val dr = (r1 - r2).absoluteValue.toDouble()
        val dg = (g1 - g2).absoluteValue.toDouble()
        val db = (b1 - b2).absoluteValue.toDouble()
        return (dr + dg + db) / 3.0 / 255.0
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
        const val PROGRESS_INDICATOR_OFFSET_X_PROPORTION = 0.041796875
        const val PROGRESS_INDICATOR_WIDTH_PROPORTION = 0.002734375
        const val PROGRESS_INDICATOR_OFFSET_Y_PROPORTION = 0.2319444444
        const val PROGRESS_INDICATOR_HEIGHT_PROPORTION = 0.7
        const val PLAYER_INDICATOR_COLOR = 0xEC003D
        const val PLAYER_INDICATOR_COLOR_TOLERANCE = 0.15
        const val PLAYER_INDICATOR_MAX_HEIGHT_PROPORTION = 0.006951340616
    }
}