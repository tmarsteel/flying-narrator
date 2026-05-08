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
import io.github.tmarsteel.flyingnarrator.ui.reactive.MousePositionSignal
import io.github.tmarsteel.flyingnarrator.ui.reactive.plusAssign
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.ImageIcon
import javax.swing.JButton

class AddObstacleRouteEditingTool(
    val makeType: () -> RouteViewModel.ObstacleModel.Type,
) : RouteEditingTool {
    private val typeExample by lazy(makeType)
    private val icon by lazy {
        ImageIcon(ObstacleComponent.iconFor(signalOf(typeExample)).value)
    }
    override fun makeToolbarButton(routeComponent: RouteComponent): JButton {
        return JButton(icon).apply {
            toolTipText  = "Add a ${typeExample::class.simpleName}"
            addActionListener {
                routeComponent.activeTool = Activation(makeType, routeComponent, this)
                this.isSelected = true
            }
        }
    }

    private class Activation(
        val makeType: () -> RouteViewModel.ObstacleModel.Type,
        val routeComponent: RouteComponent,
        val button: JButton?,
    ) : RouteEditingTool.Activation {
        override val cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

        val mousePositionInRouteComponent: Signal<Point?> = MousePositionSignal(routeComponent)
        val pointedLocation = combine(mousePositionInRouteComponent, routeComponent.routeTransform) { p, routeTransform ->
            if (p == null) return@combine null
            routeTransform.inverseTransform(p, null).let {
                Vector3(it.x, it.y, 0.0)
            }
        }

        val targetLocation = pointedLocation.scan(null as RouteViewModel.PreciseLocation?) { previousRouteLocation, pointedLocation ->
            if (pointedLocation == null) {
                return@scan previousRouteLocation
            }

            val searchRange = MovableLocationOnRouteComponent.getLocationSearchWindowAroundPreviousLocation(
                routeComponent.routeModel,
                previousRouteLocation
            )
            routeComponent.routeModel.findPreciseLocationClosestTo(pointedLocation, searchRange)
        }
            .map { it ?: routeComponent.routeModel.start.value }

        val indicator = LocationOnRouteComponent(targetLocation)

        init {
            routeComponent.add(indicator)
        }

        override fun onMouseClicked(event: MouseEvent) {
            routeComponent.routeModel.obstacles += RouteViewModel.ObstacleModel(
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