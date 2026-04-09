package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.detectFeatures
import io.github.tmarsteel.flyingnarrator.easportswrc.EASportsWRCCleanGhostRouteReader
import io.github.tmarsteel.flyingnarrator.trackSegments
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.io.path.readText
import kotlin.system.exitProcess

class RouteEditorApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (_: Exception) {
            }

            val inputFilePath = args.firstOrNull() ?: run {
                println("Usage: RouteEditorApp <input-file>")
                JOptionPane.showMessageDialog(null, "Provide an input file as CLI argument", "Error", JOptionPane.ERROR_MESSAGE)
                exitProcess(1)
            }

            val route = EASportsWRCCleanGhostRouteReader(Paths.get(inputFilePath).readText())
                .read()

            val features = route.trackSegments().detectFeatures()

            val routeComponent = ScrollableRouteComponent(RouteComponent(route, features))
            val window = JFrame()
            window.layout = BorderLayout()
            window.add(routeComponent, BorderLayout.CENTER)
            window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            window.maximizedBounds
            window.extendedState = window.extendedState or JFrame.MAXIMIZED_BOTH
            window.isVisible = true
            Thread.sleep(100);
            routeComponent.fitScaleToSize()
        }
    }
}