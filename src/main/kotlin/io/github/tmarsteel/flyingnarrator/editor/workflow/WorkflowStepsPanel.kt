package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.FlatClientProperties
import io.github.fenrur.signal.operators.map
import io.github.tmarsteel.flyingnarrator.ui.reactive.ReactiveJPanel
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JTabbedPane
import javax.swing.SwingConstants
import javax.swing.event.ChangeEvent
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class WorkflowStepsPanel(private val workflow: Workflow<*, *>) : ReactiveJPanel() {
    private var lastValidStepIndex = workflow.currentStep.value.index

    private val tabs = JTabbedPane()
    init {
        layout = BorderLayout()
        for ((index, step) in workflow.steps.withIndex()) {
            tabs.insertTab("${index + 1}. ${step.name}", step.icon, null, step.description, index)
        }
        add(tabs, BorderLayout.CENTER)

        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_AREA_ALIGNMENT, FlatClientProperties.TABBED_PANE_ALIGN_CENTER)
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_ICON_PLACEMENT, SwingConstants.TOP)
        tabs.tabPlacement = JTabbedPane.BOTTOM

        tabs.selectedIndex = lastValidStepIndex
        updateEnabledStatus()
        tabs.addChangeListener(this::onTabsChanged)

        workflow.currentStep.map { it.index }.subscribeOn(lifecycle) {
            if (it == lastValidStepIndex) {
                return@subscribeOn
            }
            check(tabChangeIsInResponseToOutsideStateChange.compareAndSet(expectedValue = false, newValue = true))
            tabs.selectedIndex = it
            this.lastValidStepIndex = it
            tabChangeIsInResponseToOutsideStateChange.store(false)
            updateEnabledStatus()
        }
    }

    private var tabChangeIsInResponseToOutsideStateChange = AtomicBoolean(false)

    private fun onTabsChanged(e: ChangeEvent) {
        if (e.source !== tabs) {
            return
        }
        val targetStepIndex = tabs.selectedIndex
        if (targetStepIndex == lastValidStepIndex) {
            return
        }

        if (tabChangeIsInResponseToOutsideStateChange.load()) {
            check(targetStepIndex == workflow.currentStep.value.index)
            return
        }

        if (targetStepIndex < lastValidStepIndex) {
            val targetStep = workflow.steps[targetStepIndex]
            val allowStepBack = if (workflow.hasAnyManualChangesSinceStep(tabs.selectedIndex)) {
                JOptionPane.showConfirmDialog(
                    rootPane,
                    "You have applied manual changes since '${targetStep.name}'. Those will be discarded if you go back. Continue?",
                    "Discard changes?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                ) == JOptionPane.YES_OPTION
            } else {
                true
            }

            if (!allowStepBack) {
                tabs.selectedIndex = lastValidStepIndex
                return
            }

            workflow.goBackTo(targetStepIndex)
        }
        check(tabs.selectedIndex == lastValidStepIndex + 1) {
            "this shouldn't have happened, can only advance one step at a time"
        }

        workflow.advanceToNextStep()
    }

    private fun updateEnabledStatus() {
        for (index in 0 until tabs.tabCount) {
            tabs.setEnabledAt(index, index <= lastValidStepIndex + 1)
        }
    }
}