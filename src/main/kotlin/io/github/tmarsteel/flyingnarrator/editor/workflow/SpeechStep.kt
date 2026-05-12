package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.pacenote.AudioPacenotes
import javax.swing.JComponent
import javax.swing.JLabel

object SpeechStep : WorkflowStep<WorkflowStep.State, AudioPacenotes> {
    override val name = "Speech"
    override val description = "Generate or record speech for the pacenotes"
    override val icon = FlatSVGIcon(this::class.java.getResource("speech.svg"))

    override fun initializeState(workflow: Workflow): WorkflowStep.State {
        return object : WorkflowStep.State {
            override val hasAnyManualChanges = false
        }
    }

    override fun stateToOutput(state: WorkflowStep.State): AudioPacenotes {
        TODO()
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
}