package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.tmarsteel.flyingnarrator.route.Route
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * TODO: actually implement importing
 */
object ImportTrackStep : WorkflowStep<Route, Route> {
    override val name = "Import Track"
    override val description = "Import track data from DiRT Rally 2.0"
    override val icon: Icon = FlatSVGIcon(this::class.java.getResource("geometry.svg"))

    override fun buildUI(input: Route): WorkflowStep.Instance<Route> {
        return object : WorkflowStep.Instance<Route> {
            override val swingComponent: JComponent = JLabel("TODO")
            override val hasAnyManualChanges: Boolean = false

            override fun getCopyOfCurrentOutputState(): Route {
                return input
            }
        }
    }
}