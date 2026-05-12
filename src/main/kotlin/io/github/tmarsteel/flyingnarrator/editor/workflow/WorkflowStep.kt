package io.github.tmarsteel.flyingnarrator.editor.workflow

import io.github.fenrur.signal.Signal
import javax.swing.Icon
import javax.swing.JComponent

interface WorkflowStep<TState : WorkflowStep.State, out Out> {
    val name: String
    val description: String
    val icon: Icon

    fun initializeState(workflow: Workflow): TState
    fun buildUI(state: TState): Instance<TState>
    fun stateToOutput(state: TState): Out

    interface Instance<out TState : State> {
        val swingComponent: JComponent
        val isComplete: Signal<Boolean>
        val hasAnyManualChanges: Signal<Boolean>
        fun getCopyOfCurrentState(): TState
    }

    interface State {
        val hasAnyManualChanges: Boolean
    }
}