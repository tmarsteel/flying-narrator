package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.tmarsteel.flyingnarrator.pacenote.AudioPacenotes
import io.github.tmarsteel.flyingnarrator.route.Speedmap
import javax.swing.JComponent
import javax.swing.JLabel

object QualityCheckStep : WorkflowStep<Pair<AudioPacenotes, Speedmap>, Pair<AudioPacenotes, Speedmap>> {
    override val name = "Check"
    override val description = "Quality check"
    override val icon = FlatSVGIcon(this::class.java.getResource("qc.svg"))

    override fun buildUI(input: Pair<AudioPacenotes, Speedmap>): WorkflowStep.Instance<Pair<AudioPacenotes, Speedmap>> {
        return object : WorkflowStep.Instance<Pair<AudioPacenotes, Speedmap>> {
            override val swingComponent: JComponent = JLabel("TODO")
            override val hasAnyManualChanges: Boolean = false

            override fun getCopyOfCurrentOutputState(): Pair<AudioPacenotes, Speedmap> {
                return input
            }
        }
    }
}