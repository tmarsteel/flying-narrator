package io.github.tmarsteel.flyingnarrator.dirtrally2

import java.awt.GraphicsDevice
import java.awt.Robot
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

class ScreenCaptureRaceProgressMonitor(
    val sourceScreen: GraphicsDevice,
    samplingInterval: Duration = StageProgressReporter.OPTIMAL_SAMPLING_INTERVAL,
    extrapolationInterval: Duration = 50.milliseconds,
) {
    private val reporter = StageProgressReporter()
    private val indicatorArea = reporter.getCropAreaForFrameSize(sourceScreen.defaultConfiguration.bounds.width, sourceScreen.defaultConfiguration.bounds.height)
    private val robot = Robot(sourceScreen)
    private val listeners = mutableSetOf<RaceProgressListener>()

    /**
     * Listeners do not receive any retroactive events upon registration.
     */
    fun addProgressListener(listener: RaceProgressListener) {
        listeners.add(listener)
    }

    fun removeProgressListener(listener: RaceProgressListener) {
        listeners.remove(listener)
    }

    /**
     * Progress will be sampled from the game at this interval.
     */
    var samplingInterval: Duration = samplingInterval
        set(value) {
            field = value
            monitoringThread.get()
                ?.takeIf { it.isAlive }
                ?.interrupt()
        }

    /**
     * Progress will be extrapolated at this interval from the last sampled true progress.
     * Set to an equal or greater value [samplingInterval] to disable extrapolation.
     */
    var extrapolationInterval: Duration = extrapolationInterval
        set(value) {
            field = value
            monitoringThread.get()
                ?.takeIf { it.isAlive }
                ?.interrupt()
        }

    @Volatile
    private var shouldStop = false
    private var monitoringThread = AtomicReference<Thread?>(null)

    /**
     * Call this method when the player is in the "hold handbrake" or countdown stage of the race. Listeners
     * will receive callbacks from then on.
     * @return whether monitoring was started as a result of this call. False if it is already running.
     */
    fun start(): Boolean {
        val localCurrentThread = monitoringThread.get()
        if (localCurrentThread != null && localCurrentThread.isAlive) {
            return false
        }

        val newThread = Thread(::monitoringThreadMain, "DR2ProgressMonitor")
        newThread.isDaemon = true
        if (monitoringThread.compareAndSet(localCurrentThread, newThread)) {
            shouldStop = false
            newThread.start()
            return true
        } else {
            // race condition, try again
            return start()
        }
    }

    /**
     * asynchronously stops the monitoring
     */
    fun stop() {
        shouldStop = true
        monitoringThread.get()
            ?.takeIf { it.isAlive }
            ?.interrupt()
    }

    private fun monitoringThreadMain() {
        try {
            listeners.forEach { it.onProgressReportingStarted() }

            var state: MonitorState = InitialMonitorState()
            state.activate()
            while (!shouldStop) {
                val (nextState, delay) = state.tick()
                if (nextState !== state) {
                    state = nextState
                    state.activate()
                }

                if (delay.isPositive()) {
                    try {
                        Thread.sleep(delay.inWholeMilliseconds)
                    } catch (_: InterruptedException) { }
                }
            }
        }
        finally {
            listeners.forEach { it.onProgressReportingStopped() }
        }
    }

    private fun sampleSingleProgressFraction(): Double {
        val indicatorImage = robot.createScreenCapture(indicatorArea)
        return reporter.getProgressFromProgressIndicatorInGameFrame(indicatorImage)
    }

    private sealed interface MonitorState {
        fun activate() {}
        fun tick(): Pair<MonitorState, Duration>
    }

    private inner class InitialMonitorState : MonitorState {
        override fun tick(): Pair<MonitorState, Duration> {
            val initialProgress = sampleSingleProgressFraction()
            return if (initialProgress < 0.0) {
                Pair(UnreliableProgressMonitorState(), samplingInterval)
            } else {
                Pair(GoodReadingMonitorState(initialProgress), samplingInterval)
            }
        }
    }

    private inner class GoodReadingMonitorState(
        private val initialMeasuredProgress: Double,
    ) : MonitorState {
        private var lastSampledProgress = initialMeasuredProgress
        private var progressLastSampledAt = TimeSource.Monotonic.markNow()
        private var measuredVelocityAtLastProgressSample = 0.0
        private var lastPublishedProgress: Double by Delegates.notNull()
        private var progressLastPublishedAt: TimeSource.Monotonic.ValueTimeMark by Delegates.notNull()

        private fun publishProgress(fraction: Double, isExtrapolated: Boolean) {
            check(fraction >= 0.0)
            lastPublishedProgress = fraction
            progressLastPublishedAt = TimeSource.Monotonic.markNow()
            listeners.forEach { it.onProgress(fraction, isExtrapolated) }
        }

        private fun shouldPublishProgress(progress: Double): Boolean {
            if (progress > lastPublishedProgress) {
                return true
            }

            if (progressLastPublishedAt.elapsedNow() < samplingInterval * 2) {
                // don't jitter back if extrapolation has overshot
                return false
            }

            return true
        }

        override fun activate() {
            publishProgress(initialMeasuredProgress, false)
        }

        override fun tick(): Pair<MonitorState, Duration> {
            val elapsedSinceLastSample = progressLastSampledAt.elapsedNow()
            if (elapsedSinceLastSample >= StageProgressReporter.OPTIMAL_SAMPLING_INTERVAL) {
                val nextSampledProgress = sampleSingleProgressFraction()
                if (nextSampledProgress < 0.0) {
                    return Pair(UnreliableProgressMonitorState(), samplingInterval)
                }

                if (nextSampledProgress > lastSampledProgress) {
                    measuredVelocityAtLastProgressSample = (nextSampledProgress- lastSampledProgress) / elapsedSinceLastSample.toDouble(DurationUnit.SECONDS)
                        .coerceAtLeast(0.0)
                }
                lastSampledProgress = nextSampledProgress
                progressLastSampledAt = TimeSource.Monotonic.markNow()
                if (shouldPublishProgress(nextSampledProgress)) {
                    publishProgress(nextSampledProgress, false)
                }

                return Pair(this, extrapolationInterval)
            }

            val extrapolatedProgress = lastSampledProgress + measuredVelocityAtLastProgressSample * elapsedSinceLastSample.toDouble(DurationUnit.SECONDS)
            if (shouldPublishProgress(extrapolatedProgress)) {
                publishProgress(extrapolatedProgress, true)
            }
            val nextSampleIn = -(progressLastSampledAt + StageProgressReporter.OPTIMAL_SAMPLING_INTERVAL).elapsedNow()
            return Pair(
                this,
                extrapolationInterval.coerceAtMost(nextSampleIn),
            )
        }
    }

    private inner class UnreliableProgressMonitorState : MonitorState {
        override fun activate() {
            listeners.forEach { it.onProgressUnreliable() }
        }

        override fun tick(): Pair<MonitorState, Duration> {
            val measuredProgress = sampleSingleProgressFraction()
            if (measuredProgress >= 0.0) {
                listeners.forEach { it.onProgressBackToReliable() }
                return Pair(GoodReadingMonitorState(measuredProgress), extrapolationInterval.coerceAtLeast(samplingInterval))
            } else {
                return Pair(this, samplingInterval)
            }
        }
    }
}