package io.github.tmarsteel.flyingnarrator.editor.workflow

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import kotlin.reflect.KClass

class Workflow private constructor(
    val steps: List<WorkflowStep<*, *>>,
    private val stepStates: HashMap<WorkflowStep<*, *>, WorkflowStep.State>,
) {
    private var _currentStepInstance = steps
        .indexOfLast { it in stepStates }
        .takeIf { it >= 0 }
        .let { it ?: 0 }
        .let(::instantiateStep)
        .let(::mutableSignalOf)
    val currentStepInstance: Signal<IndexedValue<WorkflowStep.Instance<*>>> = _currentStepInstance

    private val stepChangeMutex = Any()

    fun <O> expectStepOutput(step: WorkflowStep<*, O>): O = expectStepOutput(step::class)
    fun <O> expectStepOutput(stepClazz: KClass<out WorkflowStep<*, O>>): O {
        val matchingSteps = steps.filter { stepClazz.isInstance(it) }
        when (matchingSteps.size) {
            0 -> throw IllegalArgumentException("There is no step of type ${stepClazz.simpleName} in this workflow")
            1 -> {
                val step = matchingSteps.single()
                val state = stepStates[step]
                    ?: throw IllegalStateException("Unmet workflow dependency: a result/state for step $step is not yet available.")

                @Suppress("UNCHECKED_CAST")
                return (step as WorkflowStep<in WorkflowStep.State, O>).stateToOutput(state)
            }
            else -> throw IllegalArgumentException("The workflow step type ${stepClazz.simpleName} is ambiguous, multiple steps in this workflow match: $matchingSteps")
        }
    }

    /**
     * @return whether any of the results between the step at [stepIndex] and the [currentStepInstance] (inclusive)
     * contain any manual changes (see [WorkflowStep.State.hasAnyManualChanges])
     */
    fun hasAnyManualChangesSinceStep(stepIndex: Int): Boolean {
        for (step in steps.subList(stepIndex + 1, steps.size)) {
            val stepState = stepStates[step] ?: continue
            if (stepState.hasAnyManualChanges) {
                return true
            }
        }

        if (currentStepInstance.value.value.hasAnyManualChanges.value) {
            return true
        }

        return false
    }

    fun advanceToNextStep() {
        synchronized(stepChangeMutex) {
            val (stepIndex, stepInstance) = currentStepInstance.value
            val stepState = stepInstance.getCopyOfCurrentState()
            stepStates[steps[stepIndex]] = stepState
            _currentStepInstance.value = instantiateStep(stepIndex + 1)
        }
    }

    fun goBackTo(stepIndex: Int) {
        synchronized(stepChangeMutex) {
            val currentStepIndex = _currentStepInstance.value.index
            require(stepIndex <= currentStepIndex) { "cannot go back to step #$stepIndex, only ${currentStepIndex + 1} steps have been completed" }

            steps.subList(stepIndex + 1, steps.size).forEach(stepStates::remove)
            _currentStepInstance.value = instantiateStep(stepIndex)
        }
    }

    private fun instantiateStep(index: Int): IndexedValue<WorkflowStep.Instance<*>> {
        val step = steps[index]
        val state = stepStates[step] ?: step.initializeState(this)
        @Suppress("UNCHECKED_CAST")
        val instance = (step as WorkflowStep<in WorkflowStep.State, *>).buildUI(state)

        return IndexedValue(index, instance)
    }

    companion object {
        fun startNew(steps: List<WorkflowStep<*, *>>): Workflow = Workflow(steps, HashMap())
    }
}