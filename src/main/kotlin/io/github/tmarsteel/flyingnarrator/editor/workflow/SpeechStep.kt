package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.tmarsteel.flyingnarrator.pacenote.AudioPacenotes
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom
import javax.swing.JComponent
import javax.swing.JLabel

object SpeechStep : WorkflowStep<List<PacenoteAtom>, AudioPacenotes> {
    override val name = "Speech"
    override val description = "Generate or record speech for the pacenotes"
    override val icon = FlatSVGIcon(this::class.java.getResource("speech.svg"))

    override fun buildUI(input: List<PacenoteAtom>): WorkflowStep.Instance<AudioPacenotes> {
        return object : WorkflowStep.Instance<AudioPacenotes> {
            override val swingComponent: JComponent = JLabel("TODO")
            override val hasAnyManualChanges: Boolean = false

            override fun getCopyOfCurrentOutputState(): AudioPacenotes {
                TODO()
            }
        }
    }
}