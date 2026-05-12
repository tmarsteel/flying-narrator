package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.route.Route
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * TODO: remove the static route passthrough and actually implement importing
 */
class ImportTrackStep(val route: Route) : WorkflowStep<ImportTrackStep.State, Route> {
    override val name = "Import Track"
    override val description = "Import track data from DiRT Rally 2.0"
    override val icon: Icon = FlatSVGIcon(this::class.java.getResource("geometry.svg"))

    override fun initializeState(workflow: Workflow): State {
        return State(route)
    }

    override fun stateToOutput(state: State): Route {
        return state.route
    }

    override fun buildUI(state: State): WorkflowStep.Instance<State> {
        return object : WorkflowStep.Instance<State> {
            private val doneButton = JButton("done")
            override val swingComponent = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
                add(JLabel("TODO"))
                add(doneButton)
            }
            override val hasAnyManualChanges = signalOf(false)
            override val isComplete = mutableSignalOf(false)
            init {
                doneButton.addActionListener {
                    isComplete.value = true
                }
            }

            override fun getCopyOfCurrentState(): State {
                return state
            }
        }
    }

    class State(
        val route: Route,
    ) : WorkflowStep.State {
        override val hasAnyManualChanges = false
    }
}