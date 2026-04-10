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

private fun radiusToSeverity(radius: Double): PacenoteItem.Corner.Severity {
    // TODO: calibrate, especially 3-5
    return when (radius) {
        in 0.0..10.0 -> PacenoteItem.Corner.Severity.SQUARE
        in 10.0..25.0 -> PacenoteItem.Corner.Severity.ONE
        in 25.0..35.0 -> PacenoteItem.Corner.Severity.TWO
        in 35.0..50.0 -> PacenoteItem.Corner.Severity.THREE
        in 50.0..70.0 -> PacenoteItem.Corner.Severity.FOUR
        in 70.0..80.0 -> PacenoteItem.Corner.Severity.FIVE
        in 80.0..<95.0 -> PacenoteItem.Corner.Severity.SIX
        else -> PacenoteItem.Corner.Severity.SLIGHT
    }
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
    val radii = averageRadii(significantSegments,
        CORNER_RADIUS_AVERAGE_WINDOW_SIZE
    )
    val radiiDerivatives = radii.derivative()
    val openingOrClosingSections = radiiDerivatives
        .consecutiveRuns { dr -> dr.dRadius.absoluteValue > 1.0 }
        .toMutableList()

    // ignore tightening and opening at the start/end of corners, can be an artifact of truning into the corner
    openingOrClosingSections.firstOrNull()?.let { (startsAtIndex, section) ->
        val preceedingDistance = significantSegments.subList(0, startsAtIndex).sumOf { it.arcLength }
        val isHead = preceedingDistance < CORNER_SECTION_MIN_LENGTH
        val tightens = section.map { it.dRadius }.average().sign < 0
        val lengthProportion = section.sumOf { it.length } / corner.length
        if (isHead && tightens && lengthProportion <= CORNER_HEAD_ELISION_THRESHOLD) {
            openingOrClosingSections.removeFirst()
        }
    }
    openingOrClosingSections.lastOrNull()?.let { (startsAtIndex, section) ->
        val trailingDistance =
            significantSegments.subList(startsAtIndex + section.size, significantSegments.size).sumOf { it.arcLength }
        val isTail = trailingDistance < CORNER_SECTION_MIN_LENGTH
        val opens = section.map { it.dRadius }.average().sign > 0
        val lengthProportion = section.sumOf { it.length } / corner.length
        if (isTail && opens && lengthProportion <= CORNER_TAIL_ELISION_THRESHOLD) {
            openingOrClosingSections.removeFirst()
        }
    }

    if (openingOrClosingSections.isEmpty()) {
        return listOf(TmpCornerSection.steadyCurvature(significantSegments))
    }

    val tmpSections = mutableListOf<TmpCornerSection>()
    val unsteadyCurvatureSectionsByStartIndex = openingOrClosingSections.toMap()
    var idx = 0
    while (idx < significantSegments.size) {
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
            val indexOfNextSection =
                unsteadyCurvatureSectionsByStartIndex.keys.filter { it > idx }.minOrNull() ?: significantSegments.size
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

private data class AveragedRadius(
    val startsAtIndex: Int,
    val length: Double,
    val radius: Double,
)

private data class DerivedRadius(
    val startsAtIndex: Int,
    val length: Double,
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
            length + other.length
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

            return TmpCornerSection(section, radius, radius, severity, severity, section.sumOf { it.arcLength })
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
    return zipWithNext { a, b -> DerivedRadius(a.startsAtIndex, a.length, (b.radius - a.radius) / a.length) }
}

