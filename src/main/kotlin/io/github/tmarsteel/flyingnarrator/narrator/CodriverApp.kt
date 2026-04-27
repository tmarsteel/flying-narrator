package io.github.tmarsteel.flyingnarrator.narrator

import io.github.tmarsteel.flyingnarrator.audio.ClipQueue
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReader
import io.github.tmarsteel.flyingnarrator.dirtrally2.RaceProgressListener
import io.github.tmarsteel.flyingnarrator.dirtrally2.ScreenCaptureRaceProgressMonitor
import io.github.tmarsteel.flyingnarrator.editor.RouteComponent
import io.github.tmarsteel.flyingnarrator.io.FlyingNarratorJsonFormat
import io.github.tmarsteel.flyingnarrator.pacenote.CuedPacenoteAudio
import io.github.tmarsteel.flyingnarrator.pacenote.Lookahead
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAudio
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.route.Speedmap
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sumOf
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.times
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
import kotlin.io.path.readText
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
    private val pacenotes: CuedPacenoteAudio
    private val codriverClipQueue = ClipQueue()
    init {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
        }

        route = DirtRally2RouteReader(Paths.get(args[0])).read()
        speedmap = Speedmap.fromFile(Paths.get(args[1]))
        val pacenoteAudio = FlyingNarratorJsonFormat.decodeFromString<PacenoteAudio>(
            Paths.get(args[2]).readText(Charsets.UTF_8)
        )
        pacenotes = CuedPacenoteAudio.cueue(pacenoteAudio, routeLength, speedmap, Lookahead.ofConstantDistance(0.meters))

        routeComponent = RouteComponent(route, emptyList())
        routeComponent.distanceMarkersEvery = 500.meters
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

    val progressMonitor = ScreenCaptureRaceProgressMonitor(GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice)

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
                codriverClipQueue.stop()
                for (introCall in pacenotes.intro) {
                    codriverClipQueue.queue(introCall.clip)
                }
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
                codriverClipQueue.stop()
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
                    if (progressBeforeUpdate < fraction) {
                        pacenotes.findTriggeredCues(progressBeforeUpdate * routeLength, fraction * routeLength)
                            .forEach {
                                codriverClipQueue.queue(it.clip)
                            }
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