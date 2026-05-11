package io.github.tmarsteel.flyingnarrator.editor.workflow

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.editor.AddCornerFeatureAnnotationTool
import io.github.tmarsteel.flyingnarrator.editor.AddObstacleFeatureAnnotationTool
import io.github.tmarsteel.flyingnarrator.editor.FeatureAnnotationViewModel
import io.github.tmarsteel.flyingnarrator.editor.RouteComponent
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
        route: Route,
    ) : WorkflowStep.Instance<Pair<Route, List<Feature>>>  {
        val viewModel = FeatureAnnotationViewModel(route)
        override val swingComponent = JPanel()

        init {
            swingComponent.layout = BorderLayout()

            val routeComponent = RouteComponent(route).also {
                it.routeStyling.update { rs -> rs.copy(distanceMarkersEvery = 500.meters) }
            }

            viewModel.corners.bridgeToStatefulOn(
                routeComponent.lifecycle,
                routeComponent::addRouteShapedComponent,
                routeComponent::removeRouteShapedComponent
            ) { corner ->
                CornerUIRouteFeature(route, corner)
            }
            viewModel.obstacles.bridgeToChildComponents(routeComponent) { obstacle ->
                ObstacleComponent(viewModel, obstacle)
            }

            routeComponent.add(StartComponent(route))
            routeComponent.add(FinishComponent(route))

            Feature.discoverIn(route)
                .filterIsInstance<Feature.Corner>()
                .forEach { viewModel.corners += viewModel.makeCornerModel(it) }

            val toolbar = JToolBar(JToolBar.VERTICAL)

            for (tool in TOOLS) {
                toolbar.add(tool.makeToolbarButton(routeComponent, viewModel))
            }

            val scrollableRouteComponent = ScrollableRouteComponent(routeComponent)

            swingComponent.add(toolbar, BorderLayout.WEST)
            swingComponent.add(scrollableRouteComponent, BorderLayout.CENTER)
            swingComponent.name = "features_step"
        }

        override val hasAnyManualChanges = signalOf(false) // TODO!
        override val isComplete = signalOf(true)

        override fun getCopyOfCurrentOutputState(): Pair<Route, List<Feature>> {
            TODO()
        }
    }

    private val TOOLS = listOf(
        AddObstacleFeatureAnnotationTool({ FeatureAnnotationViewModel.ObstacleModel.Type.Chicane() }),
        AddObstacleFeatureAnnotationTool({ FeatureAnnotationViewModel.ObstacleModel.Type.Crest }),
        AddObstacleFeatureAnnotationTool({ FeatureAnnotationViewModel.ObstacleModel.Type.Dip }),
        AddObstacleFeatureAnnotationTool({ FeatureAnnotationViewModel.ObstacleModel.Type.Jump }),
        AddObstacleFeatureAnnotationTool({ FeatureAnnotationViewModel.ObstacleModel.Type.Narrows }),
        AddObstacleFeatureAnnotationTool({ FeatureAnnotationViewModel.ObstacleModel.Type.Widens }),
        AddCornerFeatureAnnotationTool(),
    )
}