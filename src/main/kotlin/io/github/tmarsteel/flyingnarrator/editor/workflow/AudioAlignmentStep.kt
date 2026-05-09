package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.tmarsteel.flyingnarrator.pacenote.AudioPacenotes
import javax.swing.JComponent
import javax.swing.JLabel

object AudioAlignmentStep : WorkflowStep<AudioPacenotes, AudioPacenotes> {
    override val name = "Alignment"
    override val description = "Align the recorded pacenote speech to the route"
    override val icon = FlatSVGIcon(this::class.java.getResource("audio_alignment.svg"))

    override fun buildUI(input: AudioPacenotes): WorkflowStep.Instance<AudioPacenotes> {
        return object : WorkflowStep.Instance<AudioPacenotes> {
            override val swingComponent: JComponent = JLabel("TODO")
            override val hasAnyManualChanges: Boolean = false

            override fun getCopyOfCurrentOutputState(): AudioPacenotes {
                return input
            }
        }
    }
}