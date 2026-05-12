package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.route.Speedmap
import javax.swing.JComponent
import javax.swing.JLabel

object SpeedmapStep : WorkflowStep<WorkflowStep.State, Speedmap> {
    override val name: String = "Speedmap"
    override val description = "Assure the speed of the car is considered correctly"
    override val icon = FlatSVGIcon(this::class.java.getResource("speedmap.svg"))

    override fun initializeState(workflow: Workflow): WorkflowStep.State {
        return object : WorkflowStep.State {
            override val hasAnyManualChanges = false
        }
    }

    override fun buildUI(state: WorkflowStep.State): WorkflowStep.Instance<WorkflowStep.State> {
        return object : WorkflowStep.Instance<WorkflowStep.State> {
            override val swingComponent: JComponent = JLabel("TODO")
            override val hasAnyManualChanges = signalOf(false)
            override val isComplete = signalOf(true)

            override fun getCopyOfCurrentState(): WorkflowStep.State {
                return state
            }
        }
    }

    override fun stateToOutput(state: WorkflowStep.State): Speedmap {
        TODO("Not yet implemented")
    }

    /*
            args.getOrNull(1)
                ?.let(Paths::get)
                ?.let { speedmapFile ->
                    try {
                        speedmapFile.inputStream().use {
                            FlyingNarratorJsonFormat.decodeFromStream<Speedmap>(it)
                        }
                    } catch (ex: NoSuchFileException) {
                        ex.printStackTrace()
                        JOptionPane.showMessageDialog(
                            null,
                            "Could not find speedmap file  $speedmapFile",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                        null
                    }
                }
                ?.let { speedmap ->
                    thread(start = true) {
                        val totalDistance = route.sumOf { it.length }
                        while (true) {
                            routeComponent.carMarker.update { it.copy(distanceAlongTrack = 0.meters) }
                            Thread.sleep(3000)
                            val startedAt = TimeSource.Monotonic.markNow()
                            while (routeComponent.carMarker.value.distanceAlongTrack < totalDistance) {
                                routeComponent.carMarker.update { it.copy(distanceAlongTrack = speedmap.estimatePositionAtTime(startedAt.elapsedNow())) }
                                Thread.sleep(33)
                            }
                        }
                    }
                }*/
}