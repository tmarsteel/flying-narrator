package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.route.Speedmap
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Creates a [Speedmap] from a screenrecording of a race-pace run on the track.
 */
class SpeedmapFromRecordedRunCreator(
    val totalTrackDistance: Distance,
    val recordingVideoFile: Path,
) {
    fun readFileAndCreateSpeedmap(): Speedmap {
        FFmpegFrameGrabber(recordingVideoFile.toFile()).use { grabber ->
            grabber.start()
            val converter = Java2DFrameConverter()
            var lastReportTimestamp = -StageProgressReporter.OPTIMAL_SAMPLING_INTERVAL
            var nFramesReported = 0
            val progressReporter = StageProgressReporter()
            val cropArea = progressReporter.getCropAreaForFrameSize(grabber.imageWidth, grabber.imageHeight)
            val controlPoints = mutableListOf<Speedmap.ControlPoint>(
                Speedmap.ControlPoint(0.meters, 0.seconds)
            )
            var previousProgressFraction = 0.0
            var previousProgressFractionAt = Duration.ZERO
            var startAt = Duration.ZERO
            while (true) {
                val frame = grabber.grabImage() ?: break
                val frameTimestamp = frame.timestamp.microseconds
                if (frameTimestamp - lastReportTimestamp < StageProgressReporter.OPTIMAL_SAMPLING_INTERVAL) {
                    continue
                }
                val image = converter.convert(frame)
                val progressFraction = progressReporter.getProgressFromProgressIndicatorInGameFrame(image.getSubimage(cropArea.x, cropArea.y, cropArea.width, cropArea.height))
                if (previousProgressFraction == 0.0) {
                    if (progressFraction > 0.0) {
                        controlPoints.clear()
                        controlPoints.add(
                            Speedmap.ControlPoint(
                                distanceAlongTrack = 0.meters,
                                Duration.ZERO,
                            )
                        )
                        startAt = previousProgressFractionAt
                    }
                    previousProgressFraction = progressFraction
                    previousProgressFractionAt = frameTimestamp
                } else {
                    if (progressFraction > previousProgressFraction) {
                        controlPoints.add(
                            Speedmap.ControlPoint(
                                distanceAlongTrack = totalTrackDistance * progressFraction,
                                frameTimestamp - startAt,
                            )
                        )
                        previousProgressFraction = progressFraction
                        previousProgressFractionAt = frame.timestamp.microseconds
                    }
                }

                lastReportTimestamp = frame.timestamp.microseconds
                nFramesReported++
            }

            return Speedmap(controlPoints)
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val file = Paths.get(args[0])
            val startAt = args[1].toInt().milliseconds
            val distance = args[2].toDouble().meters
            val speedmap = SpeedmapFromRecordedRunCreator(
                distance,
                file,
            ).readFileAndCreateSpeedmap().compress()
            Json.encodeToStream(speedmap, System.out)
        }
    }
}