package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.Speedmap
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
    val totalTrackDistance: Double,
    val recordingVideoFile: Path,
    val startAt: Duration,
) {
    fun readFileAndCreateSpeedmap(): Speedmap {
        FFmpegFrameGrabber(recordingVideoFile.toFile()).use { grabber ->
            grabber.start()
            grabber.setTimestamp(startAt.inWholeMicroseconds)
            val converter = Java2DFrameConverter()
            var lastReportTimestamp = -StageProgressReporter.OPTIMAL_SAMPLING_INTERVAL
            var nFramesReported = 0
            val progressReporter = StageProgressReporter()
            val controlPoints = mutableListOf<Speedmap.ControlPoint>(
                Speedmap.ControlPoint(0.0, 0.seconds)
            )
            var progressFractionCarry = 0.0
            while (true) {
                val frame = grabber.grabImage() ?: break
                if (frame.timestamp.microseconds - lastReportTimestamp < StageProgressReporter.OPTIMAL_SAMPLING_INTERVAL) {
                    continue
                }
                val image = converter.convert(frame)
                val progressFraction = progressReporter.getStageProgressFromFrame(image)
                if (progressFraction >= 0.0 && progressFraction > progressFractionCarry) {
                    controlPoints.add(
                        Speedmap.ControlPoint(
                            distanceAlongTrack = totalTrackDistance * progressFraction,
                            frame.timestamp.microseconds - startAt,
                        )
                    )
                    progressFractionCarry = progressFraction
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
            val distance = args[2].toDouble()
            val speedmap = SpeedmapFromRecordedRunCreator(
                distance,
                file,
                startAt,
            ).readFileAndCreateSpeedmap().compress()
            Json.encodeToStream(speedmap, System.out)
        }
    }
}