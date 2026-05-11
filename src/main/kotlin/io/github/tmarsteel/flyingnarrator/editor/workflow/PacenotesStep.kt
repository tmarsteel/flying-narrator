package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.editor.RouteComponent
import io.github.tmarsteel.flyingnarrator.editor.ScrollableRouteComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.FinishComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.StartComponent
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom
import io.github.tmarsteel.flyingnarrator.route.Route
import java.awt.BorderLayout
import javax.swing.JPanel

object PacenotesStep : WorkflowStep<Pair<Route, List<Feature>>, List<PacenoteAtom>> {
    override val name = "Pacenotes"
    override val description = "Generate pacenotes"
    override val icon = FlatSVGIcon(this::class.java.getResource("pacenotes.svg"))

    override fun buildUI(input: Pair<Route, List<Feature>>): WorkflowStep.Instance<List<PacenoteAtom>> {
        return object : WorkflowStep.Instance<List<PacenoteAtom>> {
            override val swingComponent = JPanel()

            init {
                swingComponent.layout = BorderLayout()

                val routeComponent = RouteComponent(input.first)
                routeComponent.add(StartComponent(input.first))
                routeComponent.add(FinishComponent(input.first))

                val scrollableRouteComponent = ScrollableRouteComponent(routeComponent)

                swingComponent.add(scrollableRouteComponent, BorderLayout.CENTER)
                swingComponent.name = "pacenotes_step"
            }

            override val hasAnyManualChanges = signalOf(false)
            override val isComplete = signalOf(true)

            override fun getCopyOfCurrentOutputState(): List<PacenoteAtom> {
                TODO()
            }
        }
    }
}