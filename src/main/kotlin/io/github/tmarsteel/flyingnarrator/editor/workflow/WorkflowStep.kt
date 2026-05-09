package io.github.tmarsteel.flyingnarrator.editor.workflow

import javax.swing.Icon
import javax.swing.JComponent

interface WorkflowStep<in In, out Out> {
    val name: String
    val description: String
    val icon: Icon

    fun buildUI(input: In): Instance<Out>

    interface Instance<out Out> {
        val swingComponent: JComponent
        val hasAnyManualChanges: Boolean
        fun getCopyOfCurrentOutputState(): Out
    }
}