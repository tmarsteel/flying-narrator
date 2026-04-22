package io.github.tmarsteel.flyingnarrator.pacenote.inferred

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.times

fun Iterable<Feature>.derivePacenotes(): List<Pair<Distance, InferredPacenoteItem>> {
    val pacenoteItems = mutableListOf<Pair<Distance, InferredPacenoteItem>>()
    for (feature in this) {
        when (feature) {
            is Feature.Straight -> {
                val distance = (feature.length / ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF).toInt() * ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF
                val item = when {
                    distance < IMMEDIATE_TRANSITION_DISTANCE_THRESHOLD -> InferredPacenoteItem.ImmediateTransition
                    distance <= STRAIGHT_ELISION_DISTANCE_THRESHOLD -> InferredPacenoteItem.ShortTransition
                    else -> InferredPacenoteItem.Straight(distance)
                }
                pacenoteItems += Pair(feature.startsAtTrackDistance, item)
            }
            is Feature.Corner -> {
                if (pacenoteItems.lastOrNull()?.second is InferredPacenoteItem.Corner) {
                    pacenoteItems += Pair(feature.startsAtTrackDistance, InferredPacenoteItem.ImmediateTransition)
                }
                pacenoteItems += Pair(feature.startsAtTrackDistance, cornerFeatureToPacenoteItem(feature))
            }
        }
    }

    while (pacenoteItems.firstOrNull()?.second is InferredPacenoteItem.Transition) {
        pacenoteItems.removeFirst()
    }
    while (pacenoteItems.lastOrNull()?.second is InferredPacenoteItem.Transition) {
        pacenoteItems.removeLast()
    }

    return pacenoteItems
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

fun cornerFeatureToPacenoteItem(corner: Feature.Corner): InferredPacenoteItem {
    val radius = corner.segments.compoundRadius
    return InferredPacenoteItem.Corner(corner.direction, false, listOf(
        InferredPacenoteItem.Corner.Section(radius, radiusToSeverity(radius), radius, radiusToSeverity(radius), corner.length, emptyList())
    ))
}

