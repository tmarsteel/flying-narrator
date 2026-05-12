package io.github.tmarsteel.flyingnarrator.pacenote.inferred

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.editor.PacenoteSystem
import io.github.tmarsteel.flyingnarrator.editor.ReactiveRouteComponentChild
import io.github.tmarsteel.flyingnarrator.editor.routefeatures.LocationOnRouteComponent
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.ui.StraightPacenoteIcon
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sumOf
import java.awt.Graphics

class DefaultPacenoteSystem : PacenoteSystem<InferredPacenoteItem> {
    override fun infer(
        route: Route,
        features: List<Feature>
    ): List<InferredPacenoteItem> {
        return derivePacenotes(route, features)
    }

    override fun makeRouteChildComponent(route: Route, item: InferredPacenoteItem): ReactiveRouteComponentChild {
        val icon = when (item) {
            is InferredPacenoteItem.Straight -> StraightPacenoteIcon(item.distance)
            is InferredPacenoteItem.ImmediateTransition -> FlatSVGIcon(DefaultPacenoteSystem::class.java.getResourceAsStream(
                "/io/github/tmarsteel/flyingnarrator/pacenote_transition_into.svg"
            ))
            is InferredPacenoteItem.ShortTransition -> FlatSVGIcon(DefaultPacenoteSystem::class.java.getResourceAsStream(
                "/io/github/tmarsteel/flyingnarrator/pacenote_transition_and.svg"
            ))
            is InferredPacenoteItem.Corner -> FlatSVGIcon(DefaultPacenoteSystem::class.java.getResourceAsStream(
                "/io/github/tmarsteel/flyingnarrator/pacenote_corner_${item.sections.first().severityStart.iconFilenamePart}_${item.direction.iconFilenamePart}.svg"
            ))
        }

        val itemLength = when (item) {
            is InferredPacenoteItem.Straight -> item.distance
            is InferredPacenoteItem.Transition -> 0.meters
            is InferredPacenoteItem.Corner -> item.sections.sumOf { it.length }
        }
        val location = route.findPreciseLocation(item.subjectLocation.distanceAlongRoute + itemLength / 2)
            ?: item.subjectLocation

        return object : LocationOnRouteComponent(signalOf(location)) {
            init {
                setSize(icon.iconWidth, icon.iconHeight)
            }
            override fun paintComponent(g: Graphics) {
                icon.paintIcon(this, g, 0, 0)
            }
        }
    }

    override fun atomize(items: List<InferredPacenoteItem>): List<PacenoteAtom> {
        TODO()
    }
}