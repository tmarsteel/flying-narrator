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
 * TODO: actually implement importing
 */
object ImportTrackStep : WorkflowStep<Route, Route> {
    override val name = "Import Track"
    override val description = "Import track data from DiRT Rally 2.0"
    override val icon: Icon = FlatSVGIcon(this::class.java.getResource("geometry.svg"))

    override fun buildUI(input: Route): WorkflowStep.Instance<Route> {
        return object : WorkflowStep.Instance<Route> {
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

            override fun getCopyOfCurrentOutputState(): Route {
                return input
            }
        }
    }
}