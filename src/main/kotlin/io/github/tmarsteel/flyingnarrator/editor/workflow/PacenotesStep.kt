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

object PacenotesStep : WorkflowStep<Pair<Route, List<Feature>>, List<PacenoteAtom>> {
    override val name = "Pacenotes"
    override val description = "Generate pacenotes"
    override val icon = FlatSVGIcon(this::class.java.getResource("pacenotes.svg"))

    override fun buildUI(input: Pair<Route, List<Feature>>): WorkflowStep.Instance<List<PacenoteAtom>> {
        return Instance(input.first, input.second)
    }

    private class Instance(
        val route: Route,
        val features: List<Feature>,
    ) : WorkflowStep.Instance<List<PacenoteAtom>> {
        private val pacenoteSystem = DefaultPacenoteSystem()
        private val items = pacenoteSystem.infer(route, features)
        override val swingComponent = JPanel()

        init {
            swingComponent.layout = BorderLayout()

            val routeComponent = RouteComponent(route)
            routeComponent.add(StartComponent(route))
            routeComponent.add(FinishComponent(route))

            val scrollableRouteComponent = ScrollableRouteComponent(routeComponent)

            swingComponent.add(scrollableRouteComponent, BorderLayout.CENTER)
            swingComponent.name = "pacenotes_step"

            items.forEach {
                routeComponent.add(pacenoteSystem.makeRouteChildComponent(route, it))
            }
        }

        override val hasAnyManualChanges = signalOf(false)
        override val isComplete = signalOf(true)

        override fun getCopyOfCurrentOutputState(): List<PacenoteAtom> {
            return pacenoteSystem.atomize(items)
        }
    }
}