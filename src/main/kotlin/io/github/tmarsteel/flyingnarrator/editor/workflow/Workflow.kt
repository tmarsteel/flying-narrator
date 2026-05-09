package io.github.tmarsteel.flyingnarrator.editor.workflow

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf

class Workflow<In, Out> private constructor(
    val steps: List<WorkflowStep<*, *>>,
    /**
     * data for this workflow; the first element is the input to the first step, the following
     * elements are the outputs of the steps (which are also inputs to the following steps)
     */
    private val data: MutableList<StepInput>,
) {
    private var _currentStep = mutableSignalOf(instantiateStep(data.lastIndex))
    val currentStep: Signal<IndexedValue<WorkflowStep.Instance<*>>> = _currentStep

    private val stepChangeMutex = Any()

    /**
     * @return whether any of the results between the step at [stepIndex] and the [currentStep] (inclusive)
     * contain any manual changes (see [WorkflowStep.Instance.hasAnyManualChanges])
     */
    fun hasAnyManualChangesSinceStep(stepIndex: Int): Boolean {
        for (stepInput in data.subList(stepIndex + 1, data.lastIndex)) {
            if (stepInput.containsManualChanges) return true
        }
        return false
    }

    fun advanceToNextStep() {
        synchronized(stepChangeMutex) {
            val step = currentStep.value.value
            val stepData = StepInput(
                step.getCopyOfCurrentOutputState(),
                step.hasAnyManualChanges
            )
            data.add(stepData)
            _currentStep.value = instantiateStep(data.lastIndex)
        }
    }

    fun goBackTo(stepIndex: Int) {
        synchronized(stepChangeMutex) {
            require(stepIndex <= data.lastIndex) { "cannot go back to step #$stepIndex, only ${data.lastIndex} steps have been completed available" }

            data.subList(stepIndex, data.lastIndex).clear()
            _currentStep.value = instantiateStep(stepIndex)
        }
    }

    private fun instantiateStep(index: Int): IndexedValue<WorkflowStep.Instance<*>> {
        val input = data[index].value
        @Suppress("UNCHECKED_CAST")
        val instance = (steps[index] as WorkflowStep<Any?, *>).buildUI(input)
        return IndexedValue(index, instance)
    }

    class Builder<In, Out> private constructor(
        val steps: List<WorkflowStep<*, *>>
    ) {
        constructor(firstStep: WorkflowStep<In, Out>) : this(listOf(firstStep))

        fun <NewOut> plusStep(nextStep: WorkflowStep<Out, NewOut>) = Builder<In, NewOut>(steps + nextStep)

        fun start(seed: In) = Workflow<In, Out>(steps, mutableListOf(StepInput(seed, false)))
    }

    private data class StepInput(
        val value: Any?,
        val containsManualChanges: Boolean,
    )
}