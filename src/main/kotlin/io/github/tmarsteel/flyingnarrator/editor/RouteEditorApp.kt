package io.github.tmarsteel.flyingnarrator.editor

import com.formdev.flatlaf.FlatLightLaf
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReader
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.CornerUIRouteFeature
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.LocationOnRouteComponent
import io.github.tmarsteel.flyingnarrator.editor.workflow.AnnotateFeaturesStep
import io.github.tmarsteel.flyingnarrator.editor.workflow.AudioAlignmentStep
import io.github.tmarsteel.flyingnarrator.editor.workflow.ImportTrackStep
import io.github.tmarsteel.flyingnarrator.editor.workflow.PacenotesStep
import io.github.tmarsteel.flyingnarrator.editor.workflow.QualityCheckStep
import io.github.tmarsteel.flyingnarrator.editor.workflow.SpeechStep
import io.github.tmarsteel.flyingnarrator.editor.workflow.SpeedmapStep
import io.github.tmarsteel.flyingnarrator.editor.workflow.Workflow
import io.github.tmarsteel.flyingnarrator.editor.workflow.WorkflowStepsPanel
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.ui.reactive.ReactiveComponentLifecycle
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Paths
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.system.exitProcess

class RouteEditorApp(
    val route: Route,
) {
    private val appLifecycle = ReactiveComponentLifecycle()
    private val window = JFrame()
    private val workflow = WORKFLOW.start(route)

    init {
        window.contentPane.layout = BorderLayout()
        window.contentPane.add(WorkflowStepsPanel(workflow), BorderLayout.SOUTH)
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        window.size = Dimension(800, 600)
        window.extendedState = window.extendedState or JFrame.MAXIMIZED_BOTH

        // TODO: workflow state persistence machinery

        workflow.currentStep.subscribeOn(appLifecycle) { step ->
            SwingUtilities.invokeLater {
                (window.contentPane.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)?.let {
                    window.contentPane.remove(it)
                }
                window.contentPane.add(step.value.swingComponent, BorderLayout.CENTER)
                step.value.swingComponent.revalidate()
            }
        }

        appLifecycle.onComponentMounted()
        window.addWindowStateListener(object : WindowAdapter() {
            override fun windowStateChanged(e: WindowEvent) {
                if (e.newState == WindowEvent.WINDOW_CLOSED) {
                    appLifecycle.onComponentUnmounted()
                }
            }
        })
    }

    fun start() {
        window.isVisible = true
    }

    companion object {
        val WORKFLOW = Workflow.Builder(ImportTrackStep)
            .plusStep(AnnotateFeaturesStep)
            .plusStep(PacenotesStep)
            .plusStep(SpeechStep)
            .plusStep(AudioAlignmentStep)
            .plusStep(SpeedmapStep)
            .plusStep(QualityCheckStep)

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                UIManager.setLookAndFeel(FlatLightLaf())
                UIManager.getDefaults().apply {
                    put(CornerUIRouteFeature.KEY_DISPLAY_COLOR, Color(0x2285E1))
                    put(CornerUIRouteFeature.KEY_HOVER_COLOR, Color(0x1C78CE)) // from FlatLaf Slider.hoverThumbColor
                    put(LocationOnRouteComponent.BORDER_COLOR, Color.BLACK)
                    put(LocationOnRouteComponent.COLOR, Color(0x2285E1)) // from FlatLaf Slider.thumbColor
                }
            } catch (_: Exception) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                } catch (_: Exception) {
                }
            }

            val inputFilePath = args.firstOrNull() ?: run {
                println("Usage: RouteEditorApp <input-file>")
                JOptionPane.showMessageDialog(null, "Provide an input file as CLI argument", "Error", JOptionPane.ERROR_MESSAGE)
                exitProcess(1)
            }

            val route = DirtRally2RouteReader(Paths.get(inputFilePath)).read()

            val app = RouteEditorApp(route)
            app.start()
        }
    }
}