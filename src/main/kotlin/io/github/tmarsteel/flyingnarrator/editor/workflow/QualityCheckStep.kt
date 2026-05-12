package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.signalOf
import javax.swing.JComponent
import javax.swing.JLabel

object QualityCheckStep : WorkflowStep<WorkflowStep.State, Unit> {
    override val name = "Check"
    override val description = "Quality check"
    override val icon = FlatSVGIcon(this::class.java.getResource("qc.svg"))

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

    override fun stateToOutput(state: WorkflowStep.State) {
        return
    }
}