package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.operators.scan
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.LocationOnRouteComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.MovableLocationOnRouteComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.ObstacleComponent
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.route.LocationOnRoute
import io.github.tmarsteel.flyingnarrator.ui.reactive.MousePositionSignal
import io.github.tmarsteel.flyingnarrator.ui.reactive.plusAssign
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.ImageIcon
import javax.swing.JButton

class AddObstacleFeatureAnnotationTool(
    val makeType: () -> FeatureAnnotationViewModel.ObstacleModel.Type,
) : FeatureAnnotationTool {
    private val typeExample by lazy(makeType)
    private val icon by lazy {
        ImageIcon(ObstacleComponent.iconFor(signalOf(typeExample)).value)
    }
    override fun makeToolbarButton(
        routeComponent: RouteComponent,
        viewModel: FeatureAnnotationViewModel,
    ): JButton {
        return JButton(icon).apply {
            toolTipText  = "Add a ${typeExample::class.simpleName}"
            addActionListener {
                routeComponent.activeTool = Activation(makeType, routeComponent, viewModel,this)
                this.isSelected = true
            }
        }
    }

    private class Activation(
        val makeType: () -> FeatureAnnotationViewModel.ObstacleModel.Type,
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
            routeComponent.route.findPreciseLocationClosestTo(pointedLocation, searchRange)
        }
            .map { it ?: routeComponent.route.start }

        val indicator = LocationOnRouteComponent(targetLocation)

        init {
            routeComponent.add(indicator)
        }

        override fun onMouseClicked(event: MouseEvent) {
            viewModel.obstacles += FeatureAnnotationViewModel.ObstacleModel(
                mutableSignalOf(targetLocation.value),
                makeType(),
            )
            routeComponent.activeTool = null
        }

        override fun onDeselected() {
            routeComponent.remove(indicator)
            button?.isSelected = false
        }
    }
}