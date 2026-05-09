package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.tmarsteel.flyingnarrator.pacenote.AudioPacenotes
import io.github.tmarsteel.flyingnarrator.route.Speedmap
import javax.swing.JComponent
import javax.swing.JLabel

object SpeedmapStep : WorkflowStep<AudioPacenotes, Pair<AudioPacenotes, Speedmap>> {
    override val name: String = "Speedmap"
    override val description = "Assure the speed of the car is considered correctly"
    override val icon = FlatSVGIcon(this::class.java.getResource("speedmap.svg"))

    override fun buildUI(input: AudioPacenotes): WorkflowStep.Instance<Pair<AudioPacenotes, Speedmap>> {
        return object : WorkflowStep.Instance<Pair<AudioPacenotes, Speedmap>> {
            override val swingComponent: JComponent = JLabel("TODO")
            override val hasAnyManualChanges: Boolean = false

            override fun getCopyOfCurrentOutputState(): Pair<AudioPacenotes, Speedmap> {
                TODO()
            }
        }
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