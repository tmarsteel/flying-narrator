package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.detectFeatures
import io.github.tmarsteel.flyingnarrator.easportswrc.EASportsWRCCleanGhostRouteReader
import io.github.tmarsteel.flyingnarrator.trackSegments
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.JFrame
import javax.swing.UIManager
import kotlin.io.path.readText

class RouteEditorApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (_: Exception) {
            }

            val route =
                EASportsWRCCleanGhostRouteReader(Paths.get("./easports-wrc-tracks/10.cleanghost.json").readText())
                    .read()

            val features = route.trackSegments().detectFeatures()

            val routeComponent = RouteComponent(route, features)
            val window = JFrame()
            window.layout = BorderLayout()
            window.add(ScrollableRouteComponent(routeComponent), BorderLayout.CENTER)
            window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            window.setSize(1200, 800)
            window.isVisible = true
            routeComponent.fitScaleToSize()
        }
    }
}