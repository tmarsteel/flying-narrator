package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.tmarsteel.flyingnarrator.editor.AddCornerRouteEditingTool
import io.github.tmarsteel.flyingnarrator.editor.AddObstacleRouteEditingTool
import io.github.tmarsteel.flyingnarrator.editor.RouteComponent
import io.github.tmarsteel.flyingnarrator.editor.RouteViewModel
import io.github.tmarsteel.flyingnarrator.editor.ScrollableRouteComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.CornerUIRouteFeature
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.FinishComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.ObstacleComponent
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.StartComponent
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.ui.reactive.bridgeToChildComponents
import io.github.tmarsteel.flyingnarrator.ui.reactive.bridgeToStatefulOn
import io.github.tmarsteel.flyingnarrator.ui.reactive.plusAssign
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JToolBar

object AnnotateFeaturesStep : WorkflowStep<Route, Pair<Route, List<Feature>>> {
    override val name = "Features"
    override val description = "Annotate elements on the route, e.g. crests, chicanes, ..."
    override val icon: Icon = FlatSVGIcon(this::class.java.getResource("features.svg"))

    override fun buildUI(input: Route): WorkflowStep.Instance<Pair<Route, List<Feature>>> {
        return Instance(input)
    }

    private class Instance(
        rawRoute: Route,
    ) : WorkflowStep.Instance<Pair<Route, List<Feature>>>  {
        val route = RouteViewModel(rawRoute)
        override val swingComponent = JPanel()

        init {
            swingComponent.layout = BorderLayout()

            val routeComponent = RouteComponent(route).also {
                it.routeStyling.update { rs -> rs.copy(distanceMarkersEvery = 500.meters) }
            }

            route.corners.bridgeToStatefulOn(
                routeComponent.lifecycle,
                routeComponent::addRouteShapedComponent,
                routeComponent::removeRouteShapedComponent
            ) { corner ->
                CornerUIRouteFeature(route, corner)
            }
            route.obstacles.bridgeToChildComponents(routeComponent) { obstacle ->
                ObstacleComponent(route, obstacle)
            }

            routeComponent.add(StartComponent(route))
            routeComponent.add(FinishComponent(route))

            Feature.discoverIn(rawRoute)
                .filterIsInstance<Feature.Corner>()
                .forEach { route.corners += route.makeCornerModel(it) }

            val toolbar = JToolBar(JToolBar.VERTICAL)

            for (tool in TOOLS) {
                toolbar.add(tool.makeToolbarButton(routeComponent))
            }

            val scrollableRouteComponent = ScrollableRouteComponent(routeComponent)

            swingComponent.add(toolbar, BorderLayout.WEST)
            swingComponent.add(scrollableRouteComponent, BorderLayout.CENTER)
            swingComponent.name = "features_step"
        }

        override val hasAnyManualChanges: Boolean
            get() = false // TODO

        override fun getCopyOfCurrentOutputState(): Pair<Route, List<Feature>> {
            TODO()
        }
    }

    private val TOOLS = listOf(
        AddObstacleRouteEditingTool({ RouteViewModel.ObstacleModel.Type.Chicane() }),
        AddObstacleRouteEditingTool({ RouteViewModel.ObstacleModel.Type.Crest }),
        AddObstacleRouteEditingTool({ RouteViewModel.ObstacleModel.Type.Dip }),
        AddObstacleRouteEditingTool({ RouteViewModel.ObstacleModel.Type.Jump }),
        AddObstacleRouteEditingTool({ RouteViewModel.ObstacleModel.Type.Narrows }),
        AddObstacleRouteEditingTool({ RouteViewModel.ObstacleModel.Type.Widens }),
        AddCornerRouteEditingTool(),
    )
}