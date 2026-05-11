package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.operators.scan
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.LocationOnRouteComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.MovableLocationOnRouteComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.ObstacleComponent
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.route.LocationOnRoute
import io.github.tmarsteel.flyingnarrator.ui.reactive.MousePositionSignal
import io.github.tmarsteel.flyingnarrator.ui.reactive.plusAssign
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.ImageIcon
import javax.swing.JButton

class AddCornerFeatureAnnotationTool : FeatureAnnotationTool {
    override fun makeToolbarButton(
        routeComponent: RouteComponent,
        viewModel: FeatureAnnotationViewModel,
    ): JButton {
        return JButton(ImageIcon(ObstacleComponent.CORNER_ICON)).apply {
            toolTipText = "Mark a corner"
            addActionListener {
                routeComponent.activeTool = Activation(routeComponent, viewModel, this)
                this.isSelected = true
            }
        }
    }

    private class Activation(
        val routeComponent: RouteComponent,
        val viewModel: FeatureAnnotationViewModel,
        val button: JButton?,
    ) : FeatureAnnotationTool.Activation {
        override val cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

        val mousePositionInRouteComponent: Signal<Point?> = MousePositionSignal(routeComponent)
        val pointedLocation = combine(mousePositionInRouteComponent, routeComponent.routeTransform) { p, routeTransform ->
                if (p == null) return@combine null
                routeTransform.inverseTransform(p, null).let {
                    Vector3(it.x, it.y, 0.0)
                }
            }

        val targetLocation = pointedLocation.scan(null as LocationOnRoute?) { previousRouteLocation, pointedLocation ->
                if (pointedLocation == null) {
                    return@scan previousRouteLocation
                }

                val searchRange = MovableLocationOnRouteComponent.getLocationSearchWindowAroundPreviousLocation(
                    routeComponent.route,
                    previousRouteLocation
                )
                viewModel.route.findPreciseLocationClosestTo(pointedLocation, searchRange)
            }
                .map { it ?: routeComponent.route.start }

        val indicator = LocationOnRouteComponent(targetLocation)

        init {
            routeComponent.add(indicator)
        }

        override fun onMouseClicked(event: MouseEvent) {
            val targetLocation = targetLocation.value
            val startIndex = routeComponent.route.findPreciseLocation((targetLocation.distanceAlongRoute - HALF_CORNER_LENGTH))
                ?.segment
                ?.index
                ?: 0

            val endIndex = routeComponent.route.findPreciseLocation((targetLocation.distanceAlongRoute + HALF_CORNER_LENGTH))
                ?.segment
                ?.index
                ?: routeComponent.route.segments.lastIndex

            viewModel.corners += FeatureAnnotationViewModel.CornerModel(
                mutableSignalOf(startIndex),
                mutableSignalOf(endIndex),
                mutableSignalOf(true),
            )
            routeComponent.activeTool = null
        }

        override fun onDeselected() {
            routeComponent.remove(indicator)
            button?.isSelected = false
        }
    }

    companion object {
        val HALF_CORNER_LENGTH = 25.meters
    }
}