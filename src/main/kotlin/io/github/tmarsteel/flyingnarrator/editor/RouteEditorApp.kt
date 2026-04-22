package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.Speedmap
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReader
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sumOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.concurrent.thread
import kotlin.io.path.inputStream
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.nanoseconds

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

            val route = DirtRally2RouteReader(Paths.get(inputFilePath)).read()

            val features = Feature.discoverIn(route)

            val routeComponent = RouteComponent(route, features).also {
                it.distanceMarkersEvery = 500.meters
            }
            val scrollableRouteComponent = ScrollableRouteComponent(routeComponent)
            val window = JFrame()
            window.layout = BorderLayout()
            window.add(scrollableRouteComponent, BorderLayout.CENTER)
            window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            window.maximizedBounds
            window.extendedState = window.extendedState or JFrame.MAXIMIZED_BOTH
            window.isVisible = true
            Thread.sleep(100);
            scrollableRouteComponent.fitScaleToSize()

            args.getOrNull(1)
                ?.let(Paths::get)
                ?.let { speedmapFile ->
                    try {
                        speedmapFile.inputStream().use {
                            Json.decodeFromStream<Speedmap>(it)
                        }
                    } catch (ex: NoSuchFileException) {
                        ex.printStackTrace()
                        JOptionPane.showMessageDialog(
                            null,
                            "Could not find speedmap file  $speedmapFile",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                        null
                    }
                }
                ?.let { speedmap ->
                    thread(start = true) {
                        val totalDistance = route.sumOf { it.length }
                        while (true) {
                            routeComponent.carPositionOnTrack = 0.meters
                            Thread.sleep(3000)
                            val startedAt = System.nanoTime().nanoseconds
                            while (routeComponent.carPositionOnTrack < totalDistance) {
                                val timePassed = System.nanoTime().nanoseconds - startedAt
                                routeComponent.carPositionOnTrack = speedmap.estimatePositionAtTime(timePassed)
                                Thread.sleep(33)
                            }
                        }
                    }
                }
        }
    }
}