package io.github.tmarsteel.flyingnarrator.narrator

import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.Speedmap
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReader
import io.github.tmarsteel.flyingnarrator.dirtrally2.RaceProgressListener
import io.github.tmarsteel.flyingnarrator.dirtrally2.RaceProgressMonitor
import io.github.tmarsteel.flyingnarrator.editor.RouteComponent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.nio.file.Paths
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class CodriverApp(
    args: Array<String>,
) {
    private val routeComponent: RouteComponent
    private val window: JFrame
    private val route: Route
    private val routeLength by lazy { route.sumOf { it.length } }
    private val speedmap: Speedmap
    init {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
        }

        route = DirtRally2RouteReader(Paths.get(args[0])).read()
        speedmap = Speedmap.fromFile(Paths.get(args[1]))

        routeComponent = RouteComponent(route, emptyList())
        routeComponent.distanceMarkersEveryMeters = 500.0
        routeComponent.addComponentListener(object : ComponentListener {
            override fun componentResized(e: ComponentEvent) {
                if (e.component != routeComponent) {
                    return
                }
                routeComponent.fitScaleToSize(e.component.width, e.component.height)
            }

            override fun componentMoved(e: ComponentEvent?) {
            }

            override fun componentShown(e: ComponentEvent?) {
            }

            override fun componentHidden(e: ComponentEvent?) {
            }
        })

        window = JFrame("Narrator")
        window.layout = BorderLayout()
        window.size = Dimension(800, 600)
        window.add(routeComponent, BorderLayout.CENTER)
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    }

    val progressMonitor = RaceProgressMonitor(GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice)

    private interface AppState {
        val controlPanel: JComponent
        fun activate() {}
        fun deactivate() {}
    }

    private val armedState: AppState by lazy {
        object : AppState, RaceProgressListener {
            override val controlPanel = JPanel()
            init {
                controlPanel.layout = BoxLayout(controlPanel, BoxLayout.PAGE_AXIS)
                controlPanel.add(JLabel("Ready!").apply {
                    font = font.deriveFont(24f)
                })
                controlPanel.add(JLabel("Start the race"))
            }

            override fun activate() {
                progressMonitor.addProgressListener(this)
                progressMonitor.start()
            }

            override fun deactivate() {
                progressMonitor.removeProgressListener(this)
            }

            private var isReliable = false
            override fun onProgressUnreliable() {
                isReliable = false
            }

            override fun onProgressBackToReliable() {
                isReliable = true
                state = raceInProgressState
            }

            override fun onProgress(fraction: Double, isExtrapolated: Boolean) {
                onProgressBackToReliable()
            }

            override fun onProgressReportingStopped() {
                state = idleState
            }
        }
    }

    private val idleState: AppState = object : AppState {
        override val controlPanel = JPanel()
        val readyButton = JButton("Ready!")
        init {
            controlPanel.layout = BoxLayout(controlPanel, BoxLayout.PAGE_AXIS)
            controlPanel.add(readyButton)
            readyButton.addActionListener {
                state = armedState
            }
        }
    }

    private val raceInProgressState by lazy {
        object : AppState, RaceProgressListener {
            val clips = Paths.get("C:/Users/tobia/Desktop/Pacenote Fiddle.wav").loadClips(
                mapOf<Double, Duration>(
                    0.0 to 0.38.seconds,
                    0.1 to 1.65.seconds,
                    0.2 to 2.70.seconds,
                    0.3 to 3.95.seconds,
                    0.4 to 5.36.seconds,
                    0.5 to 6.70.seconds,
                    0.6 to 7.80.seconds,
                    0.7 to 8.93.seconds,
                    0.8 to 10.3.seconds,
                    0.9 to 11.8.seconds,
                    0.98 to 13.15.seconds,
                )
            )

            override val controlPanel = JPanel()
            private val timeLabel: JLabel
            init {
                controlPanel.layout = BoxLayout(controlPanel, BoxLayout.PAGE_AXIS)
                timeLabel = JLabel(0.seconds.toRaceTimeString()).apply {
                    font = font.deriveFont(24f)
                }
                controlPanel.add(timeLabel)
            }

            override fun activate() {
                progressMonitor.addProgressListener(this)
            }

            var raceTime: Duration = 0.seconds
                set(value) {
                    field = value
                    timeLabel.text = value.toRaceTimeString()
                }

            var racingSince: TimeSource.Monotonic.ValueTimeMark? = null
            var raceTimeBeforeLastPause: Duration = 0.seconds
            var isNearFinish = false
            var previousProgress = 0.0
            fun reset() {
                previousProgress = 0.0
                racingSince = null
                raceTimeBeforeLastPause = 0.seconds
            }

            fun startNewRace(firstProgress: Double = 0.0) {
                racingSince = TimeSource.Monotonic.markNow() - speedmap.estimateDurationUntilDistance(firstProgress * routeLength)
                raceTimeBeforeLastPause = 0.seconds
            }

            init {
                reset()
            }

            override fun onProgress(fraction: Double, isExtrapolated: Boolean) {
                if (fraction == 0.0 && previousProgress > 0.0) {
                    reset()
                }

                if (previousProgress == 0.0 && fraction > 0.0 && fraction <= 0.001) {
                    startNewRace(fraction)
                }

                if (fraction > 0.996 && !isExtrapolated) {
                    // even at 480p, we have enough resolution so that the last reported measured progress should be
                    // greater than 0.997; even at 4K/2160p, the 0.996 threshold catches the sixth-to-last measurement,
                    // so extremely close
                    isNearFinish = true
                }

                updateRaceTime()
                val progressBeforeUpdate = previousProgress
                previousProgress = fraction
                SwingUtilities.invokeLater {
                    routeComponent.carPositionOnTrack = fraction * routeLength
                    clips.entries.find { it.key in progressBeforeUpdate..fraction }?.let { (_, clip) ->
                        clip.start()
                    }
                    controlPanel.repaint()
                }
            }

            private fun updateRaceTime() {
                raceTime = raceTimeBeforeLastPause + (racingSince?.elapsedNow() ?: 0.seconds)
            }

            override fun onProgressUnreliable() {
                updateRaceTime()
                raceTimeBeforeLastPause = raceTime

                if (isNearFinish) {
                    state = raceFinishedState
                } else {
                    state = racePausedState
                }
            }

            fun onResumeRace() {
                racingSince = TimeSource.Monotonic.markNow()
                updateRaceTime()
            }

            override fun onProgressBackToReliable() {
                // should never happen, but just to be sure
                onResumeRace()
            }

            override fun onProgressReportingStopped() {
                state = unexpectedDisconnectState
            }

            override fun deactivate() {
                progressMonitor.removeProgressListener(this)
            }
        }
    }

    private val racePausedState: AppState by lazy {
        object : AppState, RaceProgressListener {
            override val controlPanel = JPanel()
            init {
                controlPanel.add(JLabel("Paused").apply {
                    font = font.deriveFont(24f)
                })
            }

            override fun activate() {
                progressMonitor.addProgressListener(this)
            }

            override fun onProgressBackToReliable() {
                raceInProgressState.onResumeRace()
                state = raceInProgressState
            }

            override fun onProgress(fraction: Double, isExtrapolated: Boolean) {
                onProgressBackToReliable()
            }

            override fun deactivate() {
                progressMonitor.removeProgressListener(this)
            }
        }
    }

    private val raceFinishedState: AppState by lazy {
        object : AppState, RaceProgressListener {
            override val controlPanel = JPanel()
            init {
                controlPanel.add(JLabel("Finished").apply {
                    font = font.deriveFont(24f)
                })
            }

            override fun onProgressBackToReliable() {
                state = raceInProgressState
            }

            override fun onProgress(fraction: Double, isExtrapolated: Boolean) {
                onProgressBackToReliable()
            }
        }
    }

    private val unexpectedDisconnectState: AppState by lazy {
        object : AppState {
            override val controlPanel = JPanel()
            init {
                controlPanel.layout = BoxLayout(controlPanel, BoxLayout.PAGE_AXIS)
                controlPanel.add(JLabel("Error").apply {
                    font = font.deriveFont(24f)
                    foreground = java.awt.Color.RED
                })
                controlPanel.add(JLabel("The progress monitoring encountered an unexpected error/disconnect."))
                val resetButton = JButton("Reset")
                controlPanel.add(resetButton)
                resetButton.addActionListener {
                    state = idleState
                }
            }
        }
    }

    private var state: AppState = idleState
        set(value) {
            field.deactivate()
            window.remove(field.controlPanel)
            field = value
            value.activate()
            window.add(value.controlPanel, BorderLayout.EAST)
            window.revalidate()
        }

    fun start() {
        window.isVisible = true
        state = idleState
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CodriverApp(args).start()
        }

        private fun Duration.toRaceTimeString(): String {
            val minutes = inWholeMinutes.toString(10).padStart(2, '0')
            val seconds = (inWholeSeconds % 60).toString(10).padStart(2, '0')
            return "$minutes:$seconds"
        }
    }
}