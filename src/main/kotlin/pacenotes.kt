package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.TrackSegment
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * Straight distances are rounded to _the next lower_ multiple of this value. E.g., `10` for 80, 90, 100, 110, 120.
 * Given `10`, `89` is reported as `80`; given `5`, `89` is reported as `85`.
 */
const val ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF = 10

/**
 * Straight sections with a length equal to or less than this (after rounding) value will be elided:
 * At the start and end of the stage, they're simply dropped, between corners they are replaced with [PacenoteItem.ShortTransition]
 */
const val STRAIGHT_ELISION_DISTANCE_THRESHOLD = 20.0

/**
 * Straight sections between two corners that are shorter than this distance will be replaced with
 * [PacenoteItem.ImmediateTransition] (instead of [PacenoteItem.ShortTransition])
 */
const val IMMEDIATE_TRANSITION_DISTANCE_THRESHOLD = 10.0

/**
 * Corners with an overall radius less than this can be considered "square"
 */
const val SQUARE_MAX_COMPOUND_RADIUS = 35.0

/**
 * If a corner has a section with a radius smaller than this, it can be considered "square"
 */
const val SQUARE_MAX_RADIUS = 10.0

/**
 * If the length of track at a radius of [SQUARE_MAX_RADIUS] is this length or more, the corner can be considered "square".
 */
const val SQUARE_CORNER_MIN_DISTANCE = 3.0

/**
 * If the [Feature.Corner.totalAngle] of a corner is in this range, it can be considered "square".
 */
val SQUARE_CORNER_TOTAL_ANGLE_RANGE = Math.toRadians(80.0)..Math.toRadians(110.0)

/**
 * corner sections have to be at least this long to be called out separately
 */
const val CORNER_SECTION_MIN_LENGTH = 35.0

/**
 * High-radius sections at the start of a corner can be elided if they occupy less than this percentage of
 * the corner distance. This filters some noise coming from steering-into-the-corner data
 */
const val CORNER_HEAD_ELISION_THRESHOLD = 0.125

/**
 * See [CORNER_HEAD_ELISION_THRESHOLD], just for the tail
 */
const val CORNER_TAIL_ELISION_THRESHOLD = CORNER_HEAD_ELISION_THRESHOLD

/**
 * If a corner has a total angle in this range, it is reported as a hairpin
 */
val HAIRPIN_TOTAL_ANGLE_RANGE = Math.toRadians(135.0)..Math.toRadians(225.0)

/**
 * Corners with a total angle of [HAIRPIN_TOTAL_ANGLE_RANGE] but a severity higher than [HAIRPIN_MAX_SEVERITY]
 * will not be abbreviated to [PacenoteItem.Hairpin].
 */
val HAIRPIN_MAX_SEVERITY = PacenoteItem.Corner.Severity.THREE

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
        io.github.tmarsteel.flyingnarrator.feature.CORNER_RADIUS_AVERAGE_WINDOW_SIZE
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

data class AveragedRadius(
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
                io.github.tmarsteel.flyingnarrator.feature.CORNER_RADIUS_AVERAGE_WINDOW_SIZE
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

fun averageRadii(segments: Iterable<TrackSegment>, acrossMeters: Double): Sequence<AveragedRadius> {
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

sealed interface PacenoteItem {
    data class Straight(val distance: Int) : PacenoteItem {
        override fun toString(): String {
            return distance.toString(10)
        }
    }
    interface Transition : PacenoteItem
    data object ImmediateTransition : Transition {
        override fun toString(): String {
            return "into"
        }
    }
    data object ShortTransition : Transition {
        override fun toString(): String {
            return "to"
        }
    }
    data class Corner(
        val direction: Feature.Corner.Direction,
        /**
         * Whether this corner is across a junction/intersection
         */
        val isAtJunction: Boolean,
        val sections: List<Section>,
    ) : PacenoteItem {
        data class Section(
            val radiusStart: Double,
            val severityStart: Severity,
            val radiusEnd: Double,
            val severityEnd: Severity,
            val length: Double,
            val modifiers: List<Modifier>,
        ) {
            override fun toString(): String {
                val sb = StringBuilder()
                sb.append(severityStart)
                sb.append("(r=")
                sb.append(radiusStart.toInt().toString())
                sb.append("m)")
                if (severityEnd != severityStart) {
                    sb.append("->")
                    sb.append(severityEnd)
                    sb.append("(r=")
                    sb.append(radiusEnd.toInt().toString())
                    sb.append("m)")
                }
                sb.append("(d=")
                sb.append(length.toInt().toString())
                sb.append("m)")
                for (modifier in modifiers) {
                    sb.append(" ")
                    sb.append(modifier.toString())
                }
                return sb.toString()
            }
        }

        override fun toString(): String {
            val sb = StringBuilder()
            if (isAtJunction) {
                sb.append("turn ")
            }
            var directionWritten = false
            var currentSeverity = sections.first().severityStart
            for (section in sections) {
                var severityChange = currentSeverity.compareTo(section.severityStart)
                when {
                    severityChange < 0 -> {
                        sb.append("opens ")
                    }

                    severityChange > 0 -> {
                        sb.append("tightens ")
                    }
                }
                if (severityChange >= 0 || section.severityStart < Severity.SLIGHT) {
                    sb.append(section.severityStart)
                }
                sb.append("(r=")
                sb.append(section.radiusStart.toInt().toString())
                sb.append("m)")
                sb.append(' ')
                if (!directionWritten) {
                    sb.append(direction)
                    sb.append(' ')
                    directionWritten = true
                }
                severityChange = section.severityStart.compareTo(section.severityEnd)
                when {
                    severityChange < 0 -> {
                        sb.append("opens ")
                    }

                    severityChange > 0 -> {
                        sb.append("tightens ")
                    }
                }

                if (severityChange != 0 && (severityChange > 0 || section.severityEnd < Severity.SLIGHT)) {
                    sb.append(section.severityEnd)
                    sb.append("(r=")
                    sb.append(section.radiusEnd.toInt().toString())
                    sb.append("m)")
                    sb.append(' ')
                }

                for (mod in section.modifiers) {
                    sb.append(' ')
                    sb.append(mod)
                }

                currentSeverity = section.severityEnd
            }

            sb.append("(d=")
            sb.append(sections.sumOf { it.length }.toInt().toString())
            sb.append("m)")

            return sb.toString()
        }

        interface Modifier : SectionModifier {
            /**
             * Non-standard corner length
             */
            data class Length(val length: Value) : Modifier {
                override fun toString() = length.toString()

                enum class Value {
                    SHORT,
                    LONG,
                    EXTRA_LONG,
                    EXTRA_EXTRA_LONG,
                    ;

                    override fun toString(): String {
                        return name.replace('_', ' ').lowercase()
                    }
                }
            }
        }

        enum class Severity {
            SQUARE,
            ONE,
            TWO,
            THREE,
            FOUR,
            FIVE,
            SIX,
            SLIGHT,
            ;

            override fun toString(): String {
                return name.lowercase()
            }
        }
    }

    data class Hairpin(
        val direction: Feature.Corner.Direction,
        val minSeverity: Corner.Severity,
    ) : PacenoteItem {
        override fun toString(): String {
            val sb = StringBuilder()
            if (minSeverity == Corner.Severity.THREE) {
                sb.append("open ")
            }
            sb.append("hairpin ")
            sb.append(direction)
            return sb.toString()
        }
    }

    /**
     * Additional information applicable to _any_ stretch of road.
     */
    interface SectionModifier {
        data object OverCrest : SectionModifier
    }
}