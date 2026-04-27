package io.github.tmarsteel.flyingnarrator.dirtrally2

/**
 * Will receive events about the progress from the [Thread] that is monitoring the progress. Blocking
 * that thread in a listener callback means blocking progress monitoring.
 */
interface RaceProgressListener {
    /**
     * Called when the monitoring thread starts
     */
    fun onProgressReportingStarted() {}

    /**
     * Called roughly every [ScreenCaptureRaceProgressMonitor.extrapolationInterval] with a new progress.
     * @param fraction the progress, between `0.0` and `1.0` inclusive.
     * @param isExtrapolated true if [fraction] is a value extrapolated from the last accurate measurement. This
     * generally happens when [ScreenCaptureRaceProgressMonitor.extrapolationInterval] is less than
     * [StageProgressReporter.OPTIMAL_SAMPLING_INTERVAL]
     */
    fun onProgress(fraction: Double, isExtrapolated: Boolean)

    /**
     * Called _once_ when the progress cannot be determined reliably. Happens e.g. when the game is paused, the race
     * is exited or the game disappears from the monitored [ScreenCaptureRaceProgressMonitor.sourceScreen] in any way.
     */
    fun onProgressUnreliable() {}

    /**
     * Called _once_ after [onProgressUnreliable] has been called and accurate/reliable progress can be measured again.
     * Happens when the game is unpaused, etc.
     */
    fun onProgressBackToReliable() {}

    /**
     * Called by the monitoring thread just before it stops itself.
     */
    fun onProgressReportingStopped() {}
}