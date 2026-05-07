package io.github.tmarsteel.flyingnarrator.editor

import com.formdev.flatlaf.FlatLightLaf
import io.github.fenrur.signal.mutableSignalOf
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReader
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.CornerUIRouteFeature
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.FinishComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.ObstacleComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.StartComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.StretchRouteShapedComponent
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.io.FlyingNarratorJsonFormat
import io.github.tmarsteel.flyingnarrator.route.Speedmap
import io.github.tmarsteel.flyingnarrator.ui.reactive.bridgeToChildComponents
import io.github.tmarsteel.flyingnarrator.ui.reactive.bridgeToStatefulOn
import io.github.tmarsteel.flyingnarrator.ui.reactive.plusAssign
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sumOf
import kotlinx.serialization.json.decodeFromStream
import java.awt.BorderLayout
import java.awt.Color
import java.nio.file.Paths
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.concurrent.thread
import kotlin.io.path.inputStream
import kotlin.system.exitProcess
import kotlin.time.TimeSource

class RouteEditorApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                UIManager.setLookAndFeel(FlatLightLaf())
                UIManager.getDefaults().apply {
                    put(CornerUIRouteFeature.KEY_DISPLAY_COLOR, Color(0x2285E1))
                    put(CornerUIRouteFeature.KEY_HOVER_COLOR, Color(0x1C78CE)) // from FlatLaf Slider.hoverThumbColor
                    put(StretchRouteShapedComponent.KEY_END_HANDLE_BORDER_COLOR, Color.BLACK)
                    put(StretchRouteShapedComponent.KEY_END_HANDLE_COLOR, Color(0x2285E1)) // from FlatLaf Slider.thumbColor
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
            val viewModel = RouteViewModel(route)

            val routeComponent = RouteComponent(viewModel).also {
                it.routeStyling.update { rs -> rs.copy(distanceMarkersEvery = 500.meters) }
            }

            viewModel.corners.bridgeToStatefulOn(
                routeComponent.lifecycle,
                routeComponent::addRouteShapedComponent,
                routeComponent::removeRouteShapedComponent
            ) { corner ->
                CornerUIRouteFeature(viewModel, corner)
            }
            viewModel.obstacles.bridgeToChildComponents(routeComponent) { obstacle ->
                ObstacleComponent(viewModel, obstacle)
            }

            routeComponent.add(StartComponent(viewModel))
            routeComponent.add(FinishComponent(viewModel))

            viewModel.obstacles += RouteViewModel.ObstacleModel(
                mutableSignalOf(viewModel.findPreciseLocation(5310.meters)!!),
                RouteViewModel.ObstacleModel.Type.Chicane(),
            )

            Feature.discoverIn(route)
                .filterIsInstance<Feature.Corner>()
                .forEach { viewModel.corners += viewModel.makeCornerModel(it) }

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
                            FlyingNarratorJsonFormat.decodeFromStream<Speedmap>(it)
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
                            routeComponent.carMarker.update { it.copy(distanceAlongTrack = 0.meters) }
                            Thread.sleep(3000)
                            val startedAt = TimeSource.Monotonic.markNow()
                            while (routeComponent.carMarker.value.distanceAlongTrack < totalDistance) {
                                routeComponent.carMarker.update { it.copy(distanceAlongTrack = speedmap.estimatePositionAtTime(startedAt.elapsedNow())) }
                                Thread.sleep(33)
                            }
                        }
                    }
                }
        }
    }
}