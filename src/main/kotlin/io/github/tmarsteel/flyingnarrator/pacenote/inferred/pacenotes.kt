package io.github.tmarsteel.flyingnarrator.pacenote.inferred

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import io.github.tmarsteel.flyingnarrator.route.LocationOnRoute
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.times

fun derivePacenotes(route: Route, features: Iterable<Feature>): List<InferredPacenoteItem> {
    val pacenoteAtoms = mutableListOf<InferredPacenoteItem>()
    for (feature in features) {
        val featureLocation = route.findPreciseLocation(feature.startsAtDistance)!!
        when (feature) {
            is Feature.Straight -> {
                val distance = (feature.length / ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF).toInt() * ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF
                val item = when {
                    distance < IMMEDIATE_TRANSITION_DISTANCE_THRESHOLD -> InferredPacenoteItem.ImmediateTransition(featureLocation)
                    distance <= STRAIGHT_ELISION_DISTANCE_THRESHOLD -> InferredPacenoteItem.ShortTransition(featureLocation)
                    else -> InferredPacenoteItem.Straight(featureLocation, distance)
                }
                pacenoteAtoms += item
            }
            is Feature.Corner -> {
                if (pacenoteAtoms.lastOrNull() is InferredPacenoteItem.Corner) {
                    pacenoteAtoms += InferredPacenoteItem.ImmediateTransition(featureLocation)
                }
                pacenoteAtoms += cornerFeatureToPacenoteItem(feature, featureLocation)
            }
        }
    }

    while (pacenoteAtoms.firstOrNull() is InferredPacenoteItem.Transition) {
        pacenoteAtoms.removeFirst()
    }
    while (pacenoteAtoms.lastOrNull() is InferredPacenoteItem.Transition) {
        pacenoteAtoms.removeLast()
    }

    return pacenoteAtoms
}

private val severityMinRadius = sequenceOf(
    0.0.meters to InferredPacenoteItem.Corner.Severity.ONE,
    55.0.meters to InferredPacenoteItem.Corner.Severity.TWO,
    75.0.meters to InferredPacenoteItem.Corner.Severity.THREE,
    90.0.meters to InferredPacenoteItem.Corner.Severity.FOUR,
    150.0.meters to InferredPacenoteItem.Corner.Severity.FIVE,
    185.0.meters to InferredPacenoteItem.Corner.Severity.SIX,
    225.0.meters to InferredPacenoteItem.Corner.Severity.SLIGHT,
)
private fun radiusToSeverity(radius: Distance): InferredPacenoteItem.Corner.Severity {
    return severityMinRadius.last { (minRadius, _) -> radius >= minRadius }.second
}

private fun cornerFeatureToPacenoteItem(corner: Feature.Corner, location: LocationOnRoute): InferredPacenoteItem {
    val radius = corner.segments.compoundRadius
    return InferredPacenoteItem.Corner(
        location,
        corner.direction,
        false,
        listOf(
            InferredPacenoteItem.Corner.Section(radius, radiusToSeverity(radius), radius, radiusToSeverity(radius), corner.length, emptyList())
        )
    )
}

