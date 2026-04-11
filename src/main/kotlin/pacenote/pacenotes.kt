package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.averageOf
import io.github.tmarsteel.flyingnarrator.consecutiveRuns
import io.github.tmarsteel.flyingnarrator.feature.CORNER_RADIUS_AVERAGE_WINDOW_SIZE
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.TrackSegment
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import io.github.tmarsteel.flyingnarrator.firstAndLast
import io.github.tmarsteel.flyingnarrator.mergeConsecutiveIf
import kotlin.math.absoluteValue
import kotlin.math.sign

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
    val sections = findCornerSections(corner).map { it.toPacenote() }
    if (corner.totalAngle.absoluteValue in HAIRPIN_TOTAL_ANGLE_RANGE) {
        val minSeverity = sections.minOf { it.severityStart.coerceAtMost(it.severityEnd) }
        if (minSeverity <= HAIRPIN_MAX_SEVERITY) {
            val maxSeverity = sections.maxOf { it.severityStart.coerceAtLeast(it.severityEnd) }
            val hasSimilarSeverities = (maxSeverity.ordinal - minSeverity.ordinal).absoluteValue <= 1
            if (hasSimilarSeverities) {
                return PacenoteItem.Hairpin(corner.direction, minSeverity)
            }
        }
    }
    return PacenoteItem.Corner(corner.direction, false, sections)
}

private fun findCornerSections(corner: Feature.Corner): List<TmpCornerSection> {
    val significantSegments = corner.segments

    if (corner.length <= CORNER_SECTION_MIN_LENGTH) {
        return listOf(TmpCornerSection.steadyCurvature(significantSegments))
    }

    // first, find opens/closes sections where the radius is steadily changing
    val radii = averageRadii(significantSegments, CORNER_RADIUS_AVERAGE_WINDOW_SIZE)
    val radiiDerivatives = radii.derivative()
    val openingOrClosingSections = radiiDerivatives
        .consecutiveRuns { dr -> dr.dRadius.absoluteValue > SEVERITY_CHANGE_DRADIUS_THRESHOLD }
        .toMutableList()

    // ignore tightening and opening at the start/end of corners, can be an artifact of turning into the corner
    var actualCornerStartsAtIdx = 0
    openingOrClosingSections.firstOrNull()?.let { (startsAtIndex, section) ->
        val preceedingDistance = significantSegments.subList(0, startsAtIndex).sumOf { it.arcLength }
        val isHead = preceedingDistance < CORNER_SECTION_MIN_LENGTH
        val tightens = section.map { it.dRadius }.average().sign < 0
        val sectionLength = section.last().from.aroundDistance - section.first().from.aroundDistance
        val lengthProportion = sectionLength / corner.length
        if (isHead && tightens && lengthProportion <= CORNER_HEAD_ELISION_THRESHOLD) {
            openingOrClosingSections.removeFirst()
            actualCornerStartsAtIdx = startsAtIndex + section.size
        }
    }
    var actualCornerEndsAtIdxExcl = significantSegments.size
    openingOrClosingSections.lastOrNull()?.let { (startsAtIndex, section) ->
        val trailingDistance = significantSegments.subList(startsAtIndex + section.size, significantSegments.size).sumOf { it.arcLength }
        val isTail = trailingDistance < CORNER_SECTION_MIN_LENGTH
        val opens = section.map { it.dRadius }.average().sign > 0
        val sectionLength = section.last().from.aroundDistance - section.first().from.aroundDistance
        val lengthProportion = sectionLength / corner.length
        if (isTail && opens && lengthProportion <= CORNER_TAIL_ELISION_THRESHOLD) {
            openingOrClosingSections.removeFirst()
            actualCornerEndsAtIdxExcl = startsAtIndex
        }
    }

    if (openingOrClosingSections.isEmpty()) {
        return listOf(TmpCornerSection.steadyCurvature(significantSegments.subList(actualCornerStartsAtIdx, actualCornerEndsAtIdxExcl)))
    }

    val tmpSections = mutableListOf<TmpCornerSection>()
    val unsteadyCurvatureSectionsByStartIndex = openingOrClosingSections.toMap()
    var idx = actualCornerStartsAtIdx
    while (idx < actualCornerEndsAtIdxExcl) {
        val unsteadySection = unsteadyCurvatureSectionsByStartIndex[idx]
        if (unsteadySection != null) {
            tmpSections.add(
                TmpCornerSection.openingOrTightening(
                    significantSegments.subList(
                        idx,
                        idx + unsteadySection.size
                    )
                )
            )
            idx += unsteadySection.size
        } else {
            val indexOfNextSection = unsteadyCurvatureSectionsByStartIndex.keys.filter { it > idx }.minOrNull() ?: significantSegments.size
            tmpSections.add(TmpCornerSection.steadyCurvature(significantSegments.subList(idx, indexOfNextSection)))
            idx = indexOfNextSection
        }
    }

    return tmpSections.mergeConsecutiveIf(
        shouldMerge = { a, b ->
            a.length < CORNER_SECTION_MIN_LENGTH || b.length < CORNER_SECTION_MIN_LENGTH || a.severityEnd == b.severityStart
        },
        merge = TmpCornerSection::mergeWithSuccessor,
    ).toList()
}

internal data class AveragedRadius(
    val startsAtIndex: Int,
    val windowStartsAtDistance: Double,
    val windowLength: Double,
    val radius: Double,
) {
    val aroundDistance: Double = windowStartsAtDistance + windowLength / 2.0
}

internal data class DerivedRadius(
    val from: AveragedRadius,
    /**
     * change in radius, radius-meters per arcLength-meter
     */
    val dRadius: Double,
)

private data class TmpCornerSection(
    val segments: List<TrackSegment>,
    val radiusStart: Double,
    val radiusEnd: Double,
    val severityStart: PacenoteItem.Corner.Severity,
    val severityEnd: PacenoteItem.Corner.Severity,
    val length: Double,
) {
    fun mergeWithSuccessor(other: TmpCornerSection): TmpCornerSection {
        return TmpCornerSection(
            segments + other.segments,
            radiusStart,
            other.radiusEnd,
            severityStart,
            other.severityStart,
            length + other.length,
        )
    }

    fun toPacenote(): PacenoteItem.Corner.Section {
        return PacenoteItem.Corner.Section(radiusStart, severityStart, radiusEnd, severityEnd, length, emptyList())
    }

    companion object {
        fun steadyCurvature(section: List<TrackSegment>): TmpCornerSection {
            val totalAngle = section.sumOf { it.angleToNext }
            var radius = section.compoundRadius
            var severity = radiusToSeverity(radius)
            if (radius <= SQUARE_MAX_COMPOUND_RADIUS && totalAngle.absoluteValue in SQUARE_CORNER_TOTAL_ANGLE_RANGE) {
                val squareRadiusSegments = section
                    .asSequence()
                    .filter { it.radiusToNext <= SQUARE_MAX_RADIUS }
                if (squareRadiusSegments.sumOf { it.arcLength } >= SQUARE_CORNER_MIN_DISTANCE) {
                    radius = squareRadiusSegments.averageOf { it.radiusToNext }
                    severity = PacenoteItem.Corner.Severity.SQUARE
                }
            }

            return TmpCornerSection(
                section,
                radius,
                radius,
                severity,
                severity,
                section.sumOf { it.arcLength },
            )
        }

        fun openingOrTightening(section: List<TrackSegment>): TmpCornerSection {
            val (radiusStart, radiusEnd) = averageRadii(section,
                CORNER_RADIUS_AVERAGE_WINDOW_SIZE
            )
                .map { it.radius }
                .firstAndLast()
            return TmpCornerSection(
                section,
                radiusStart,
                radiusEnd,
                radiusToSeverity(radiusStart),
                radiusToSeverity(radiusEnd),
                section.sumOf { it.arcLength },
            )
        }
    }
}

private fun averageRadii(segments: Iterable<TrackSegment>, acrossMeters: Double): Sequence<AveragedRadius> {
    return sequence {
        val currentWindowSequence = ArrayDeque<TrackSegment>((acrossMeters * 2.0).toInt())
        var currentWindowStartsAtIndex = 0
        var distanceInWindow = 0.0
        var yielded = false
        suspend fun SequenceScope<AveragedRadius>.yieldCurrent() {
            val windowLength = currentWindowSequence.sumOf { it.arcLength }
            val averageRadius = currentWindowSequence.sumOf { it.radiusToNext * it.arcLength } / windowLength
            yield(
                AveragedRadius(
                    currentWindowStartsAtIndex,
                    currentWindowSequence.first().startsAtDistance,
                    windowLength,
                    averageRadius,
                )
            )
            val removedSegment = currentWindowSequence.removeFirst()
            distanceInWindow -= removedSegment.arcLength
            currentWindowStartsAtIndex += 1
            yielded = true
        }

        for (segment in segments) {
            currentWindowSequence.addLast(segment)
            distanceInWindow += segment.arcLength
            if (distanceInWindow >= acrossMeters) {
                yieldCurrent()
            } else {
                yielded = false
            }
        }
        if (!yielded && currentWindowSequence.isNotEmpty()) {
            yieldCurrent()
        }
    }
}

private fun Sequence<AveragedRadius>.derivative(): Sequence<DerivedRadius> {
    return zipWithNext { a, b ->
        DerivedRadius(a, (b.radius - a.radius) / a.windowLength)
    }
}

