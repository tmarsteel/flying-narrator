package io.github.tmarsteel.flyingnarrator.codriver

import io.github.tmarsteel.flyingnarrator.audio.ClipQueue
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2TelemetryGameObserver
import io.github.tmarsteel.flyingnarrator.io.FlyingNarratorJsonFormat
import io.github.tmarsteel.flyingnarrator.pacenote.AudioPacenotes
import io.github.tmarsteel.flyingnarrator.pacenote.CuedAudioPacenotes
import io.github.tmarsteel.flyingnarrator.pacenote.Lookahead
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.times
import kotlinx.serialization.json.decodeFromStream
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Paths
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.io.path.inputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class CodriverApp(
    args: Array<String>,
    val gameObserver: GameObserver,
) {
    private val window: JFrame
    private val codriverClipQueue = ClipQueue()
    init {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
        }

        window = JFrame("Narrator")
        window.layout = BorderLayout()
        window.size = Dimension(800, 600)
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    }

    private interface AppState {
        val controlPanel: JComponent
        fun activate() {}
        fun deactivate() {}
    }

    private val idleState: AppState = object : AppState, GameObserver.Listener {
        override val controlPanel = JPanel()
        val stateLabel = JLabel("Ready")
        init {
            controlPanel.layout = BoxLayout(controlPanel, BoxLayout.PAGE_AXIS)
            controlPanel.add(stateLabel)
        }

        override fun activate() {
            gameObserver.addListener(this)
        }

        override fun onRunStarting(route: CodriverRouteInfo) {
            state = RaceInProgressState(route)
        }

        override fun deactivate() {
            gameObserver.removeListener(this)
        }
    }

    private inner class RaceInProgressState(
        val routeInfo: CodriverRouteInfo,
    ) : AppState, GameObserver.Listener {
        override val controlPanel = JPanel()
        private val timeLabel: JLabel
        init {
            controlPanel.layout = BoxLayout(controlPanel, BoxLayout.PAGE_AXIS)
            timeLabel = JLabel(0.seconds.toRaceTimeString()).apply {
                font = font.deriveFont(24f)
            }
            controlPanel.add(timeLabel)
            controlPanel.add(JLabel(routeInfo.displayName.value))
        }

        var raceTime: Duration = 0.seconds
            set(value) {
                field = value
                timeLabel.text = value.toRaceTimeString()
            }

        val cuedPacenotes = CuedAudioPacenotes.cue(
            Paths.get("audio-pacenotes.json").inputStream().use {
                FlyingNarratorJsonFormat.decodeFromStream<AudioPacenotes>(it)
            },
            routeInfo.length,
            routeInfo.speedmap,
            Lookahead.ofConstantDistance(0.meters),
        )

        var racingSince: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()
        var raceTimeBeforeLastPause: Duration = 0.seconds // TODO: use lap time from telemetry
        var isNearFinish = false
        var previousProgress = 0.0
        var paused = false

        override fun onRunProgress(fraction: Double) {
            if (paused) {
                return
            }

            if (fraction > 0.99) {
                isNearFinish = true
            }

            updateRaceTime()
            val progressBeforeUpdate = previousProgress
            previousProgress = fraction
            SwingUtilities.invokeLater {
                if (progressBeforeUpdate < fraction) {
                    cuedPacenotes.findTriggeredCues(progressBeforeUpdate * routeInfo.length, fraction * routeInfo.length)
                        .forEach {
                            codriverClipQueue.queue(it.clip)
                        }
                }
                controlPanel.repaint()
            }
        }

        private fun updateRaceTime() {
            raceTime = raceTimeBeforeLastPause + racingSince.elapsedNow()
        }

        override fun onGamePaused() {
            updateRaceTime()
            raceTimeBeforeLastPause = raceTime
            paused = true
        }

        override fun onGameResumed() {
            paused = false
            racingSince = TimeSource.Monotonic.markNow()
            updateRaceTime()
        }

        override fun onStageEnded() {
            state = raceFinishedState
        }

        override fun activate() {
            gameObserver.addListener(this)
            codriverClipQueue.stop()
        }

        override fun deactivate() {
            gameObserver.removeListener(this)
        }
    }

    private val raceFinishedState: AppState by lazy {
        object : AppState, GameObserver.Listener {
            override val controlPanel = JPanel()
            init {
                controlPanel.add(JLabel("Finished").apply {
                    font = font.deriveFont(24f)
                })
            }

            override fun activate() {
                gameObserver.addListener(this)
            }

            override fun onRunStarting(route: CodriverRouteInfo) {
                state = RaceInProgressState(route)
            }

            override fun deactivate() {
                gameObserver.removeListener(this)
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
            CodriverApp(args, DirtRally2TelemetryGameObserver).start()
        }

        private fun Duration.toRaceTimeString(): String {
            val minutes = inWholeMinutes.toString(10).padStart(2, '0')
            val seconds = (inWholeSeconds % 60).toString(10).padStart(2, '0')
            return "$minutes:$seconds"
        }
    }
}