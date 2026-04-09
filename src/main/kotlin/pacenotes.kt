package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.RepeatFirstAndLastSequence.Companion.repeatFirstAndLast
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.withSign

/**
 * Straight distances are rounded to _the next lower_ multiple of this value. E.g., `10` for 80, 90, 100, 110, 120.
 * Given `10`, `89` is reported as `80`; given `5`, `89` is reported as `85`.
 */
const val ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF = 10

/**
 * The [TrackSegment.radiusToNext] is capped to this value
 */
const val MAX_REPORTED_RADIUS = 750.0

/**
 * The minimum [compoundRadius] for a part of the route to be considered a straight section
 */
const val STRAIGHTISH_MIN_RADIUS = 400.0

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
 * The radii measured throughout a corner will be average across a distance of [CORNER_RADIUS_AVERAGE_WINDOW_SIZE]
 * meters before analyzing for severities
 */
const val CORNER_RADIUS_AVERAGE_WINDOW_SIZE = 7.5

/**
 * [TrackSegment.severity] absolute less than this is considered straight
 */
const val CORNER_SEVERITY_THRESHOLD = 0.075

/**
 * High-radius sections at the start of a corner can be elided if they occopy less than this percentage of
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
                val distance =
                    (feature.length.toInt() / ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF) * ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF
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

data class TrackSegment(
    val roadSegment: RoadSegment,
    val startsAtDistance: Double,
    val radiusToNext: Double,
    val angleToNext: Double,
    val arcLength: Double,
) {
    var severitySet: Boolean = false
        private set
    var severity: Double = 0.0
        get() {
            check(severitySet) { "severity not set yet" }
            return field
        }
        set(value) {
            check(!severitySet) { "severity already set" }
            severitySet = true
            field = value
        }
}

fun Route.trackSegments(): List<TrackSegment> {
    val segments = sequence {
        val windows = this@trackSegments
            .asSequence()
            .repeatFirstAndLast()
            .windowed(size = 3, step = 1, partialWindows = false)

        var distanceAlongTrack = 0.0
        for ((prev, current, next) in windows) {
            val startsAtDistance = distanceAlongTrack
            distanceAlongTrack += current.length2d

            var (radius, arcLength) = radiusAndArcLengthOfCorner(prev, current, next)
            radius = radius.coerceAtMost(MAX_REPORTED_RADIUS)
            val angle = current.angleTo(next)
            yield(TrackSegment(current, startsAtDistance, radius, angle, arcLength))
        }
    }.toList()

    segments
        .windowsWhere { it.sumOf { it.arcLength } >= CORNER_RADIUS_AVERAGE_WINDOW_SIZE }
        .forEach { avgWindow ->
            val windowLength = avgWindow.sumOf { it.arcLength }
            val exactWindowCenterDistance = avgWindow.first().startsAtDistance + windowLength / 2.0
            val windowCenterSegment = avgWindow.minBy { (it.startsAtDistance - exactWindowCenterDistance).absoluteValue }
            if (windowCenterSegment.severitySet) {
                return@forEach
            }
            val windowRadius = avgWindow.sumOf { it.radiusToNext * it.arcLength } / windowLength
            val windowAngle = avgWindow.sumOf { it.angleToNext }
            windowCenterSegment.severity = ((STRAIGHTISH_MIN_RADIUS - windowRadius.coerceAtMost(STRAIGHTISH_MIN_RADIUS)) / STRAIGHTISH_MIN_RADIUS)
                .pow(3.0)
                .withSign(windowAngle)
        }

    segments.asSequence()
        .consecutiveRuns { !it.severitySet }
        .forEach { (runStartIdx, segmentsWithoutSeverity) ->
            val severityStart = if (runStartIdx == 0) 0.0 else segments[runStartIdx - 1].severity
            val severityEnd = if (runStartIdx + segmentsWithoutSeverity.size == segments.size) 0.0 else segments[runStartIdx + segmentsWithoutSeverity.size].severity
            val severityStep = (severityEnd - severityStart) / (segmentsWithoutSeverity.size + 1)
            var currentSeverity = severityStart + severityStep
            for (segment in segmentsWithoutSeverity) {
                segment.severity = currentSeverity
                currentSeverity += severityStep
            }
        }

    return segments
}

/**
 * Constructs a circle that passes through [previous] and `previous + current` with the constraint
 * that [next] is tangential to the circle at the location `previous + current`.
 * @return the radius of a curved road around [current] described by the given vectors.
 */
private fun radiusAndArcLengthOfCorner(previous: Vector3, current: Vector3, next: Vector3): Pair<Double, Double> {
    val radial1 = MLine(previous, previous.angleBisectorWith(current))
    val radial2 = MLine(previous + current, current.angleBisectorWith(next))
    val center = radial1.intersect2d(radial2) ?: return Pair(Double.POSITIVE_INFINITY, current.length2d)
    val centerToBeginOfCurrent = (previous - center)
    val centerToEndOfCurrent = ((previous + current) - center)
    val cornerRadius = centerToEndOfCurrent.length2d
    val circleSectionAngle = centerToBeginOfCurrent.angleTo(centerToEndOfCurrent).absoluteValue
    val arcLength = cornerRadius * circleSectionAngle
    return Pair(cornerRadius, arcLength)
}

private fun Vector3.angleBisectorWith(bent: Vector3): Vector3 {
    val halfAngle = this.angleTo(-bent) / 2.0
    val rotated = this.rotate2dCounterClockwise(-halfAngle)
    return rotated
}

/**
 * A line defined by a vector from origin to one point on the line
 * and a vector defining the direction of the line.
 */
private class MLine(
    val somePoint: Vector3,
    val direction: Vector3,
) {
    /**
     * Treats this as a two-dimensional line defined by [Vector3.x] and [Vector3.y] and returns the point of intersection
     * between `this` and [other].
     * @return the intersection point (in [Vector3.x] and [Vector3.y] with [Vector3.z] being `0`),
     *         or `null` in case the lines are parallel or identical.
     */
    fun intersect2d(other: MLine): Vector3? {
        val cgMinusFj = other.direction.x * this.direction.y - this.direction.x * other.direction.y
        if (cgMinusFj == 0.0) {
            // parallel or identical
            return null
        }

        val nForOther = (-this.somePoint.y * this.direction.x - other.somePoint.x * this.direction.y + this.somePoint.x * this.direction.y + this.direction.x * other.somePoint.y) / cgMinusFj
        val intersectionPoint = other.getPoint(nForOther)
        if (!this.contains2d(intersectionPoint)) {
            return null
        }

        return intersectionPoint
    }

    /**
     * @return a point on this line, computed by [somePoint] + [n] * [direction].
     */
    fun getPoint(n: Double): Vector3 {
        return somePoint + direction * n
    }

    /**
     * @return whether [point] is on `this`, disregarding [direction].
     */
    fun contains2d(point: Vector3, tolerance: Double = 0.00001): Boolean {
        if (this.direction.x == 0.0) {
            return point.x == this.somePoint.x
        }

        if (this.direction.y == 0.0) {
            return point.y == this.somePoint.y
        }

        val factorForX = (point.x - this.somePoint.x) / this.direction.x
        val factorForY = (point.y - this.somePoint.y) / this.direction.y
        val amountOff = (factorForX - factorForY).absoluteValue / direction.length2d
        return amountOff <= tolerance
    }
}

fun List<TrackSegment>.detectFeatures(): List<Feature> {
    val buffer = ArrayDeque<TrackSegment>()
    val features = mutableListOf<Feature>()
    var state: FeatureDetectionState = FeatureDetectionState.Straight
    for (segment in this) {
        buffer.add(segment)
        state = state.process(buffer, features)
    }

    if (buffer.isNotEmpty()) {
        state.finish(buffer, features)
    }

    if (features.size > 1) {
        val last = features[features.lastIndex]
        val preLast = features[features.lastIndex - 1]
        if (last is Feature.Straight && preLast is Feature.Straight) {
            features.removeLast()
            features.add(Feature.Straight(preLast.startsAtTrackDistance, preLast.length + last.length))
        } else if (last is Feature.Corner && preLast is Feature.Corner) {
            features.removeLast()
            features.add(Feature.Corner(preLast.segments + last.segments))
        }
    }

    return features
}

private sealed interface FeatureDetectionState {
    fun process(buffer: ArrayDeque<TrackSegment>, features: MutableList<Feature>): FeatureDetectionState
    fun finish(buffer: ArrayDeque<TrackSegment>, features: MutableList<Feature>)

    object Straight : FeatureDetectionState {
        override fun process(
            buffer: ArrayDeque<TrackSegment>,
            features: MutableList<Feature>
        ): FeatureDetectionState {
            val cornerEntryIdx = detectCornerEntry(buffer)
            if (cornerEntryIdx < 0) {
                return this
            }

            val straightSegments = buffer.subList(0, cornerEntryIdx).toList()
            features += Feature.Straight(straightSegments)
            repeat(straightSegments.size) {
                buffer.removeFirst()
            }

            return Corner(buffer.first().severity.sign)
        }

        private fun detectCornerEntry(buffer: ArrayDeque<TrackSegment>): Int {
            return buffer.indexOfLast { it.severity.absoluteValue >= CORNER_SEVERITY_THRESHOLD }
        }

        override fun finish(
            buffer: ArrayDeque<TrackSegment>,
            features: MutableList<Feature>
        ) {
            features += Feature.Straight(buffer.toList())
            buffer.clear()
        }
    }

    class Corner(val severitySign: Double) : FeatureDetectionState {
        override fun process(
            buffer: ArrayDeque<TrackSegment>,
            features: MutableList<Feature>
        ): FeatureDetectionState {
            val straightStartsAtIndex = detectCornerExitToStraight(buffer)
            if (straightStartsAtIndex >= 0) {
                val cornerSegments = buffer.subList(0, straightStartsAtIndex).toList()
                features += Feature.Corner(cornerSegments)
                repeat(cornerSegments.size) {
                    buffer.removeFirst()
                }

                return Straight
            }

            val cornerDirectionChangesAtIndex = detectCornerDirectionChange(buffer)
            if (cornerDirectionChangesAtIndex >= 0) {
                val cornerSegments = buffer.subList(0, cornerDirectionChangesAtIndex).toList()
                features += Feature.Corner(cornerSegments)
                repeat(cornerSegments.size) {
                    buffer.removeFirst()
                }

                return Corner(-severitySign)
            }

            return this
        }

        override fun finish(
            buffer: ArrayDeque<TrackSegment>,
            features: MutableList<Feature>
        ) {
            features += Feature.Corner(buffer.toList())
            buffer.clear()
        }

        private fun detectCornerExitToStraight(buffer: ArrayDeque<TrackSegment>): Int {
            return buffer.indexOfLast { it.severity.absoluteValue < CORNER_SEVERITY_THRESHOLD }
        }

        private fun minimumStraightDistanceAfterCorner(compoundRadius: Double): Double {
            return when {
                compoundRadius <= 50.0 -> 0.0
                compoundRadius in 50.0..100.0 -> (compoundRadius - 50.0) * (20.0 / 50.0)
                else -> ((compoundRadius - 100.0) / 5.0).coerceAtMost(50.0)
            }
        }

        private fun detectCornerDirectionChange(buffer: ArrayDeque<TrackSegment>): Int {
            return buffer.indexOfLast { it.severity.sign == -severitySign }
        }
    }
}

sealed interface Feature {
    val startsAtTrackDistance: Double
    val length: Double

    data class Straight(
        override val startsAtTrackDistance: Double,
        override val length: Double,
    ) : Feature {
        constructor(segments: List<TrackSegment>) : this(
            segments.first().startsAtDistance,
            segments.sumOf { it.arcLength },
        )
    }

    class Corner(
        val segments: List<TrackSegment>,
    ) : Feature {
        override val startsAtTrackDistance: Double = segments.first().startsAtDistance
        val totalAngle: Double = segments.sumOf { it.angleToNext }
        override val length: Double by lazy { segments.sumOf { it.arcLength } }

        val direction get() = if (totalAngle > 0) Direction.RIGHT else Direction.LEFT

        override fun toString(): String {
            return "Corner(totalAngle=$totalAngle, totalDistance=$length, direction=$direction)"
        }

        enum class Direction {
            LEFT,
            RIGHT,
            ;

            override fun toString(): String {
                return name.lowercase()
            }
        }
    }
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
    val radii = averageRadii(significantSegments, CORNER_RADIUS_AVERAGE_WINDOW_SIZE)
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
            val (radiusStart, radiusEnd) = averageRadii(section, CORNER_RADIUS_AVERAGE_WINDOW_SIZE)
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

private fun <T> Sequence<T>.consecutiveRuns(
    predicate: (T) -> Boolean
): Sequence<Pair<Int, List<T>>> {
    return sequence {
        var inRun = false
        var currentRunStartsAtIndex = 0
        var currentRun = mutableListOf<T>()
        for ((idx, e) in this@consecutiveRuns.withIndex()) {
            if (predicate(e)) {
                if (inRun) {
                    currentRun += e
                } else {
                    currentRun = mutableListOf(e)
                    currentRunStartsAtIndex = idx
                    inRun = true
                }
            } else {
                if (inRun) {
                    yield(Pair(currentRunStartsAtIndex, currentRun))
                    currentRun = mutableListOf()
                    inRun = false
                }
            }
        }
        if (inRun) {
            yield(Pair(currentRunStartsAtIndex, currentRun))
        }
    }
}

val List<TrackSegment>.compoundRadius: Double
    get() {
        if (size == 1) {
            return single().radiusToNext
        }

        val totalAngle = sumOf { it.angleToNext }
        if (totalAngle.absoluteValue > 2.793) {
            /** the perpendicular-line-intersection algo only works reliably for coners considerably less than 180° */
            val cutIndex = size / 2
            val part1 = subList(0, cutIndex).compoundRadius
            val part2 = subList(cutIndex, size).compoundRadius
            if (part1 == Double.POSITIVE_INFINITY) {
                return part2
            }
            if (part2 == Double.POSITIVE_INFINITY) {
                return part1
            }
            return (part1 + part2) / 2.0
        }

        val cornerStartsAt = Vector3.ORIGIN
        val cornerStart = first().roadSegment
        val cornerEndsAt = map { it.roadSegment }.reduce { acc, segment -> acc + segment }
        val cornerEnd = last().roadSegment
        val line1 = MLine(cornerStartsAt, cornerStart.rotate2d90degCounterClockwise())
        val line2 = MLine(cornerEndsAt, cornerEnd.rotate2d90degCounterClockwise())
        val center = line1.intersect2d(line2) ?: return Double.POSITIVE_INFINITY
        return (cornerEndsAt - center).length2d
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