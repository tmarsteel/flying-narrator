package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.editor.RouteComponent
import io.github.tmarsteel.flyingnarrator.editor.ScrollableRouteComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.FinishComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.StartComponent
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom
import io.github.tmarsteel.flyingnarrator.pacenote.inferred.DefaultPacenoteSystem
import io.github.tmarsteel.flyingnarrator.route.Route
import java.awt.BorderLayout
import javax.swing.JPanel

object PacenotesStep : WorkflowStep<PacenotesStep.State, List<PacenoteAtom>> {
    override val name = "Pacenotes"
    override val description = "Generate pacenotes"
    override val icon = FlatSVGIcon(this::class.java.getResource("pacenotes.svg"))

    override fun initializeState(workflow: Workflow): State {
        return State(
            workflow.expectStepOutput(ImportTrackStep::class),
            workflow.expectStepOutput(AnnotateFeaturesStep),
        )
    }

    override fun buildUI(state: State): WorkflowStep.Instance<State> {
        return Instance(state)
    }

    override fun stateToOutput(state: State): List<PacenoteAtom> {
        return state.pacenoteSystem.atomize(state.items)
    }

    private class Instance(
        val state: State,
    ) : WorkflowStep.Instance<State> {

        override val swingComponent = JPanel()

        init {
            swingComponent.layout = BorderLayout()

            val routeComponent = RouteComponent(state.route)
            routeComponent.add(StartComponent(state.route))
            routeComponent.add(FinishComponent(state.route))

            val scrollableRouteComponent = ScrollableRouteComponent(routeComponent)

            swingComponent.add(scrollableRouteComponent, BorderLayout.CENTER)
            swingComponent.name = "pacenotes_step"

            state.items.forEach {
                routeComponent.add(state.pacenoteSystem.makeRouteChildComponent(state.route, it))
            }
        }

        override val hasAnyManualChanges = signalOf(false)
        override val isComplete = signalOf(true)

        override fun getCopyOfCurrentState(): State {
            return state
        }
    }

    class State(
        val route: Route,
        val features: List<Feature>,
    ) : WorkflowStep.State {
        override val hasAnyManualChanges = false

        val pacenoteSystem = DefaultPacenoteSystem()
        val items = pacenoteSystem.infer(route, features)
    }
}