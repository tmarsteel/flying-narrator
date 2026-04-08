package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.detectFeatures
import io.github.tmarsteel.flyingnarrator.easportswrc.EASportsWRCCleanGhostRouteReader
import io.github.tmarsteel.flyingnarrator.trackSegments
import java.awt.BorderLayout
import java.awt.Point
import java.nio.file.Paths
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.io.path.readText

class RouteEditorApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val route =
                EASportsWRCCleanGhostRouteReader(Paths.get("./easports-wrc-tracks/12.cleanghost.json").readText())
                    .read()
                    .take(200)

            val features = route.trackSegments().detectFeatures()

            val routeC = RouteComponent(route, features)
            val scrollPane = JScrollPaneWithDragScroll(
                routeC,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            )
            scrollPane.isWheelScrollingEnabled = false
            scrollPane.viewport.scrollMode = JViewport.BLIT_SCROLL_MODE
            scrollPane.addMouseWheelListener { mwe ->
                val newScale = (routeC.scale - mwe.preciseWheelRotation / 100.0).coerceAtLeast(0.1)
                if (routeC.scale == newScale) {
                    return@addMouseWheelListener
                }
                val pointedComponentPosition =
                    Point(mwe.x + scrollPane.viewport.viewPosition.x, mwe.y + scrollPane.viewport.viewPosition.y)
                val pointedRoutePosition = routeC.getRoutePositionFromComponentPosition(pointedComponentPosition)
                routeC.scale = newScale
                val newComponentPosition = routeC.getComponentPositionFromRoutePosition(pointedRoutePosition)
                val dX = newComponentPosition.x - pointedComponentPosition.x
                val dY = newComponentPosition.y - pointedComponentPosition.y
                scrollPane.viewport.viewPosition =
                    Point(scrollPane.viewport.viewPosition.x + dX, scrollPane.viewport.viewPosition.y + dY)
            }
            val window = JFrame()
            window.layout = BorderLayout()
            window.add(scrollPane, BorderLayout.CENTER)
            window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            window.setSize(400, 400)
            window.isVisible = true
            routeC.fitScaleToSize()
        }
    }
}