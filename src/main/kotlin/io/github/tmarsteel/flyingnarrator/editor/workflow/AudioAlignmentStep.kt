package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.pacenote.AudioPacenotes
import javax.swing.JComponent
import javax.swing.JLabel

object AudioAlignmentStep : WorkflowStep<WorkflowStep.State, AudioPacenotes> {
    override val name = "Alignment"
    override val description = "Align the recorded pacenote speech to the route"
    override val icon = FlatSVGIcon(this::class.java.getResource("audio_alignment.svg"))

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

    override fun stateToOutput(state: WorkflowStep.State): AudioPacenotes {
        TODO("Not yet implemented")
    }
}