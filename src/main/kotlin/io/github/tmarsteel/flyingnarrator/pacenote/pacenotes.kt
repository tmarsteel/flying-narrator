package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius

fun Iterable<Feature>.derivePacenotes(): List<Pair<Double, PacenoteAtom>> {
    val pacenoteItems = mutableListOf<Pair<Double, PacenoteAtom>>()
    for (feature in this) {
        when (feature) {
            is Feature.Straight -> {
                val distance = (feature.length.toInt() / ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF) * ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF
                val item = when {
                    distance < IMMEDIATE_TRANSITION_DISTANCE_THRESHOLD -> PacenoteAtom.ImmediateTransition
                    distance <= STRAIGHT_ELISION_DISTANCE_THRESHOLD -> PacenoteAtom.ShortTransition
                    else -> PacenoteAtom.Straight(distance)
                }
                pacenoteItems += Pair(feature.startsAtTrackDistance, item)
            }
            is Feature.Corner -> {
                if (pacenoteItems.lastOrNull()?.second is PacenoteAtom.Corner) {
                    pacenoteItems += Pair(feature.startsAtTrackDistance, PacenoteAtom.ImmediateTransition)
                }
                pacenoteItems += Pair(feature.startsAtTrackDistance, cornerFeatureToPacenoteItem(feature))
            }
        }
    }

    while (pacenoteItems.firstOrNull()?.second is PacenoteAtom.Transition) {
        pacenoteItems.removeFirst()
    }
    while (pacenoteItems.lastOrNull()?.second is PacenoteAtom.Transition) {
        pacenoteItems.removeLast()
    }

    return pacenoteItems
}

private val severityMinRadius = sequenceOf(
    0.0 to PacenoteAtom.Corner.Severity.ONE,
    55.0 to PacenoteAtom.Corner.Severity.TWO,
    75.0 to PacenoteAtom.Corner.Severity.THREE,
    90.0 to PacenoteAtom.Corner.Severity.FOUR,
    150.0 to PacenoteAtom.Corner.Severity.FIVE,
    185.0 to PacenoteAtom.Corner.Severity.SIX,
    225.0 to PacenoteAtom.Corner.Severity.SLIGHT,
)
private fun radiusToSeverity(radius: Double): PacenoteAtom.Corner.Severity {
    return severityMinRadius.last { (minRadius, _) -> radius >= minRadius }.second
}

fun cornerFeatureToPacenoteItem(corner: Feature.Corner): PacenoteAtom {
    val radius = corner.segments.compoundRadius
    return PacenoteAtom.Corner(corner.direction, false, listOf(
        PacenoteAtom.Corner.Section(radius, radiusToSeverity(radius), radius, radiusToSeverity(radius), corner.length, emptyList())
    ))
}

