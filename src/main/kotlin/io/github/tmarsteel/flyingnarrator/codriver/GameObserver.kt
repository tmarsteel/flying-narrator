package io.github.tmarsteel.flyingnarrator.codriver

/**
 * An object that observes a video game and informs the codriver application on which track/pacenotes to play back.
 */
interface GameObserver {

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    interface Listener {
        /**
         * Called when it has become evident that a stage run is about to start.
         */
        fun onRunStarting(route: CodriverRouteInfo) {}

        /**
         * Called as the car progresses through the stage
         */
        fun onRunProgress(fraction: Double) {}

        fun onGamePaused() {}

        fun onGameResumed() {}

        /**
         * called when the stage run has ended; not necessarily because the finish line has been crossed, though.
         */
        fun onStageEnded() {}
    }
}