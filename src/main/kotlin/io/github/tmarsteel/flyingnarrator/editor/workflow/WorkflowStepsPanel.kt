package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.FlatClientProperties
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingConstants

class WorkflowStepsPanel(
    private val workflow: Workflow<*, *>,
    initiallySelectedIndex: Int = 0,
) : JPanel() {
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

        tabs.selectedIndex = initiallySelectedIndex
        var previousIndex = tabs.selectedIndex

        tabs.addChangeListener {
            if (it.source !== tabs) {
                return@addChangeListener
            }
            val targetStepIndex = tabs.selectedIndex
            if (targetStepIndex == previousIndex) {
                return@addChangeListener
            }

            if (targetStepIndex < previousIndex) {
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
                    tabs.selectedIndex = previousIndex
                    return@addChangeListener
                }

                workflow.goBackTo(targetStepIndex)
            }
            check(tabs.selectedIndex == previousIndex + 1) {
                "this shouldn't have happened, can only advance one step at a time"
            }

            workflow.advanceToNextStep()
            previousIndex = tabs.selectedIndex
            updateEnabledStatus()
        }
    }

    var currentStepIndex: Int
        get() = tabs.selectedIndex
        set(value) {
            tabs.selectedIndex = value
            updateEnabledStatus()
        }

    init {
        currentStepIndex = 0
    }

    private fun updateEnabledStatus() {
        for (index in 0 until tabs.tabCount) {
            tabs.setEnabledAt(index, index <= currentStepIndex + 1)
        }
    }

    companion object {
        /*private val ICONS = TileImage(
            ImageIO.read(WorkflowStepsPanel::class.java.getResource("workflow_step_icons.png")!!),
            Dimension(64, 64),
        )

        private val STEPS = listOf(
            Step("Geometry", "Obtain and correct route geometry", ImageIcon(ICONS[0])),
            Step("Elements", "Annotate elements on the route, e.g. crests, chicanes, ...", ImageIcon(ICONS[1])),
            Step("Pacenotes", "Generate pacenotes", ImageIcon(ICONS[2])),
            Step("Speech", "Generate or record speech for the pacenotes", ImageIcon(ICONS[3])),
            Step("Alignment", "Align the recorded pacenote speech to the route", ImageIcon(ICONS[4])),
            Step("Speedmap", "Assure the speed of the car is considered correctly", ImageIcon(ICONS[5])),
            Step("Check", "Quality check", ImageIcon(ICONS[6])),
        )*/
    }
}