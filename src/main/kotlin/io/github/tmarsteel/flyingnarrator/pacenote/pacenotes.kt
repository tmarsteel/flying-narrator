package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius

fun Iterable<Feature>.derivePacenotes(): List<Pair<Double, PacenoteItem>> {
    val pacenoteItems = mutableListOf<Pair<Double, PacenoteItem>>()
    for (feature in this) {
        when (feature) {
            is Feature.Straight -> {
                val distance = (feature.length.toInt() / ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF) * ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF
                val item = when {
                    distance < IMMEDIATE_TRANSITION_DISTANCE_THRESHOLD -> PacenoteItem.ImmediateTransition
                    distance <= STRAIGHT_ELISION_DISTANCE_THRESHOLD -> PacenoteItem.ShortTransition
                    else -> PacenoteItem.Straight(distance)
                }
                pacenoteItems += Pair(feature.startsAtTrackDistance, item)
            }
            is Feature.Corner -> {
                if (pacenoteItems.lastOrNull()?.second is PacenoteItem.Corner) {
                    pacenoteItems += Pair(feature.startsAtTrackDistance, PacenoteItem.ImmediateTransition)
                }
                pacenoteItems += Pair(feature.startsAtTrackDistance, cornerFeatureToPacenoteItem(feature))
            }
        }
    }

    while (pacenoteItems.firstOrNull()?.second is PacenoteItem.Transition) {
        pacenoteItems.removeFirst()
    }
    while (pacenoteItems.lastOrNull()?.second is PacenoteItem.Transition) {
        pacenoteItems.removeLast()
    }

    return pacenoteItems
}

private val severityMinRadius = sequenceOf(
    0.0 to PacenoteItem.Corner.Severity.ONE,
    55.0 to PacenoteItem.Corner.Severity.TWO,
    75.0 to PacenoteItem.Corner.Severity.THREE,
    90.0 to PacenoteItem.Corner.Severity.FOUR,
    150.0 to PacenoteItem.Corner.Severity.FIVE,
    185.0 to PacenoteItem.Corner.Severity.SIX,
    225.0 to PacenoteItem.Corner.Severity.SLIGHT,
)
private fun radiusToSeverity(radius: Double): PacenoteItem.Corner.Severity {
    return severityMinRadius.last { (minRadius, _) -> radius >= minRadius }.second
}

fun cornerFeatureToPacenoteItem(corner: Feature.Corner): PacenoteItem {
    val radius = corner.segments.compoundRadius
    return PacenoteItem.Corner(corner.direction, false, listOf(
        PacenoteItem.Corner.Section(radius, radiusToSeverity(radius), radius, radiusToSeverity(radius), corner.length, emptyList())
    ))
}

