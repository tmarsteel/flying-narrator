package io.github.tmarsteel.flyingnarrator.editor

import com.formdev.flatlaf.FlatClientProperties
import io.github.tmarsteel.flyingnarrator.ui.TileImage
import java.awt.BorderLayout
import java.awt.Dimension
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingConstants

class WorkflowStepPanel(
    private val controller: WorkflowController,
    initiallySelectedIndex: Int = 0,
) : JPanel() {
    private val tabs = JTabbedPane()
    init {
        layout = BorderLayout()
        for ((index, step) in STEPS.withIndex()) {
            tabs.insertTab("${index + 1}. ${step.name}", step.icon, null, step.tip, index)
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
            if (tabs.selectedIndex == previousIndex) {
                return@addChangeListener
            }

            if (tabs.selectedIndex < previousIndex) {
                if (!controller.tryNavigateBack(previousIndex)) {
                    tabs.selectedIndex = previousIndex
                    return@addChangeListener
                }
            }

            controller.onNavigatedForward(tabs.selectedIndex)
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

    interface WorkflowController {
        /**
         * Called when the user indicates they want to go back to a previous step.
         * @return whether navigation to the previous step was done.
         */
        fun tryNavigateBack(stepIndex: Int): Boolean

        /**
         * Called when the user indicates they want to advance the next step
         * @param stepIndex the index of the step the user wants to navigate to
         */
        fun onNavigatedForward(stepIndex: Int)
    }

    private fun updateEnabledStatus() {
        for (index in 0 until tabs.tabCount) {
            tabs.setEnabledAt(index, index <= currentStepIndex + 1)
        }
    }

    private data class Step(
        val name: String,
        val tip: String,
        val icon: Icon,
    )

    companion object {
        private val ICONS = TileImage(
            ImageIO.read(WorkflowStepPanel::class.java.getResource("workflow_step_icons.png")!!),
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
        )
    }
}