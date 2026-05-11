package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom
import io.github.tmarsteel.flyingnarrator.route.Route
import javax.swing.JComponent
import javax.swing.JLabel

object PacenotesStep : WorkflowStep<Pair<Route, List<Feature>>, List<PacenoteAtom>> {
    override val name = "Pacenotes"
    override val description = "Generate pacenotes"
    override val icon = FlatSVGIcon(this::class.java.getResource("pacenotes.svg"))

    override fun buildUI(input: Pair<Route, List<Feature>>): WorkflowStep.Instance<List<PacenoteAtom>> {
        return object : WorkflowStep.Instance<List<PacenoteAtom>> {
            override val swingComponent: JComponent = JLabel("TODO")
            override val hasAnyManualChanges = signalOf(false)
            override val isComplete = signalOf(true)

            override fun getCopyOfCurrentOutputState(): List<PacenoteAtom> {
                TODO()
            }
        }
    }
}