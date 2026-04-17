package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import javax.imageio.ImageIO

class StageProgressReporterTest : FreeSpec({
    val reporter = StageProgressReporter()

    "full game frame with only positive-delta splits" {
        val frame = ImageIO.read(StageProgressReporterTest::class.java.getResourceAsStream("full_game_frame.png"))
        val crop = reporter.getCropAreaForFrameSize(frame.width, frame.height)
        val cropped = frame.getSubimage(crop.x, crop.y, crop.width, crop.height)
        val progress = reporter.getProgressFromProgressIndicatorInGameFrame(cropped)
        progress.shouldBeWithinPercentageOf(0.3153, 0.5)
    }

    "with negative-delta split and close-in ghost" {
        val cropped = ImageIO.read(StageProgressReporterTest::class.java.getResourceAsStream("indicator_negative_delta_split_close_ghost.png"))
        val progress = reporter.getProgressFromProgressIndicatorInGameFrame(cropped)
        progress.shouldBeWithinPercentageOf(0.1821, 0.5)
    }

    "with negative-delta splits" {
        val cropped = ImageIO.read(StageProgressReporterTest::class.java.getResourceAsStream("indicator_negative_delta_split.png"))
        val progress = reporter.getProgressFromProgressIndicatorInGameFrame(cropped)
        progress.shouldBeWithinPercentageOf(0.3544, 0.5)
    }
})