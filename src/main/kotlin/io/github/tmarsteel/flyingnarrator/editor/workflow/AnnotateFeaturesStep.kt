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
import io.github.tmarsteel.flyingnarrator.feature.Feature.Straight
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.ui.reactive.bridgeToChildComponents
import io.github.tmarsteel.flyingnarrator.ui.reactive.bridgeToStatefulOn
import io.github.tmarsteel.flyingnarrator.ui.reactive.plusAssign
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JToolBar

object AnnotateFeaturesStep : WorkflowStep<FeatureAnnotationViewModel, List<Feature>> {
    override val name = "Features"
    override val description = "Annotate elements on the route, e.g. crests, chicanes, ..."
    override val icon: Icon = FlatSVGIcon(this::class.java.getResource("features.svg"))

    override fun initializeState(workflow: Workflow): FeatureAnnotationViewModel {
        val viewModel = FeatureAnnotationViewModel(workflow.expectStepOutput(ImportTrackStep::class))
        Feature.discoverIn(viewModel.route)
            .filterIsInstance<Feature.Corner>()
            .forEach { viewModel.corners += viewModel.makeCornerModel(it) }

        return viewModel
    }

    override fun buildUI(state: FeatureAnnotationViewModel): WorkflowStep.Instance<FeatureAnnotationViewModel> {
        return Instance(state)
    }

    override fun stateToOutput(state: FeatureAnnotationViewModel): List<Feature> {
        val features = mutableListOf<Feature>()
        state.corners.value.mapTo(features) { toFeature(state.route, it) }
        addInferredStraightsTo(state, features)

        features.sortBy { it.startsAtDistance }
        return features
    }

    private class Instance(
        val viewModel: FeatureAnnotationViewModel,
    ) : WorkflowStep.Instance<FeatureAnnotationViewModel>  {
        override val swingComponent = JPanel()

        init {
            swingComponent.layout = BorderLayout()

            val routeComponent = RouteComponent(viewModel.route).also {
                it.routeStyling.update { rs -> rs.copy(distanceMarkersEvery = 500.meters) }
            }

            viewModel.corners.bridgeToStatefulOn(
                routeComponent.lifecycle,
                routeComponent::addRouteShapedComponent,
                routeComponent::removeRouteShapedComponent
            ) { corner ->
                CornerUIRouteFeature(viewModel.route, corner)
            }
            viewModel.obstacles.bridgeToChildComponents(routeComponent) { obstacle ->
                ObstacleComponent(viewModel, obstacle)
            }

            routeComponent.add(StartComponent(viewModel.route))
            routeComponent.add(FinishComponent(viewModel.route))

            val toolbar = JToolBar(JToolBar.VERTICAL)

            for (tool in TOOLS) {
                toolbar.add(tool.makeToolbarButton(routeComponent, viewModel))
            }

            val scrollableRouteComponent = ScrollableRouteComponent(routeComponent)

            swingComponent.add(toolbar, BorderLayout.WEST)
            swingComponent.add(scrollableRouteComponent, BorderLayout.CENTER)
            swingComponent.name = "features_step"
        }

        override val isComplete = signalOf(true)
        override val hasAnyManualChanges = viewModel.hasAnyManualChangesSignal

        override fun getCopyOfCurrentState(): FeatureAnnotationViewModel {
            return viewModel
        }
    }

    private fun toFeature(route: Route, model: FeatureAnnotationViewModel.CornerModel): Feature.Corner {
        return Feature.Corner(route.segments.slice(model.segmentIndices.value))
    }

    /**
     * Adds a [Straight] to [features] for every section of road in [viewModel] that isn't a corner
     */
    private fun addInferredStraightsTo(viewModel: FeatureAnnotationViewModel, features: MutableList<in Straight>) {
        val cornersByStartIndex = viewModel.corners.value.sortedBy { it.indexOfFirstSegment.value }
        cornersByStartIndex[0].indexOfFirstSegment.value
            .takeIf { it > 0 }
            ?.let { firstCornerStartIdx ->
                features.add(Straight(viewModel.route.segments.slice(0.. firstCornerStartIdx)))
            }

        cornersByStartIndex
            .asSequence()
            .windowed(size = 2, step = 1, partialWindows = true)
            .forEach { cs ->
                val previousCorner = cs[0]
                val nextCorner = cs.getOrNull(1)
                val nextCornerStartsAtIndex = nextCorner?.indexOfFirstSegment?.value ?: viewModel.route.segments.size
                val straightStartsAtIndex = previousCorner.indexOfLastSegment.value + 1
                val straightEndsAtIndex = nextCornerStartsAtIndex - 1
                if (straightEndsAtIndex < straightStartsAtIndex) {
                    return@forEach // corners directly adjacent
                }
                val straightSegments = viewModel.route.segments.slice(straightStartsAtIndex .. straightEndsAtIndex)
                features.add(Straight(straightSegments))
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