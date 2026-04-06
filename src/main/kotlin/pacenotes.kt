package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.DropLastSequence.Companion.dropLast
import io.github.tmarsteel.flyingnarrator.RepeatFirstAndLastSequence.Companion.repeatFirstAndLast
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * Straight distances are rounded to this amount, e.g. `10` for 80, 90, 100, 110, 120, ...
 */
const val ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF = 10

/**
 * On corners that have little detail in the input data, it is assumed that corners extend at most
 * this distance into the straight sections before/after
 */
const val MAX_CORNER_ROUNDING_DISTANCE = 5.0

/**
 * Two [RoadSegment]s that have a radius greater than this value will be considered straight
 */
const val STRAIGHTISH_RADIUS_THRESHOLD = 120.0

/**
 * The [TrackSegment.radiusToNext] is capped to this value
 */
const val MAX_REPORTED_RADIUS = STRAIGHTISH_RADIUS_THRESHOLD * 10.0

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
 * It takes at least this number of [TrackSegment] with a [TrackSegment.radiusToNext] less than [STRAIGHTISH_RADIUS_THRESHOLD]
 * for a corner to be detected.
 */
const val CORNER_MIN_SEGMENTS = 2

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
 * When detecting radius change in a corner (opens/tightens), the data is first smoothed out by
 * taking the average radius of all vectors within [RADIUS_CHANGE_SMOOTHING_DISTANCE] meters.
 */
val RADIUS_CHANGE_SMOOTHING_DISTANCE = 10.0

fun Sequence<TrackSegment>.derivePacenotes(): List<Pair<Double, PacenoteItem>> {
    val features = detectFeatures()

    val pacenoteItems = mutableListOf<Pair<Double, PacenoteItem>>()
    for (feature in features) {
        when (feature) {
            is Feature.Straight -> {
                val distance = (feature.distance.toInt() / ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF) * ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF
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
)

fun Route.trackSegments(): Sequence<TrackSegment> {
    return sequence {
        val windows = this@trackSegments
            .asSequence()
            .capSegmentLength2d(MAX_CORNER_ROUNDING_DISTANCE)
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
    }
}

/**
 * Will split [RoadSegment]s longer than [maxLength] into two or three new [RoadSegment]s, such that the two ends
 * are at most [maxLength] long ([Vector3.length2d]).
 */
private fun Sequence<RoadSegment>.capSegmentLength2d(maxLength: Double): Sequence<RoadSegment> {
    return flatMap { segment ->
        when {
            segment.length2d <= maxLength -> return@flatMap sequenceOf(segment)
            segment.length2d <= maxLength * 2 -> {
                val half = segment.half()
                return@flatMap sequenceOf(half, half)
            }
            else -> {
                val startAndEndSection = segment.withLength2d(maxLength)
                val middleSection = segment.withLength2d(segment.length2d - maxLength * 2.0)
                return@flatMap sequenceOf(startAndEndSection, middleSection, startAndEndSection)
            }
        }
    }
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

fun Sequence<TrackSegment>.detectFeatures(): List<Feature> {
    val features = ArrayList<Feature>()
    val featureSegments = mutableListOf<TrackSegment>()
    var nCurvedSegmentsPresent = 0
    var inCorner = false
    for (segment in this) {
        if (inCorner) {
            if (segment.radiusToNext > STRAIGHTISH_RADIUS_THRESHOLD || segment.angleToNext.sign != featureSegments.last().angleToNext.sign) {
                features.add(Feature.Corner(featureSegments.first().startsAtDistance, featureSegments.toMutableList()))
                featureSegments.clear()
                inCorner = false
                nCurvedSegmentsPresent = 0
            }
        }

        featureSegments.add(segment)

        if (!inCorner) {
            if (segment.radiusToNext <= STRAIGHTISH_RADIUS_THRESHOLD) {
                nCurvedSegmentsPresent++
            }
            if (nCurvedSegmentsPresent >= CORNER_MIN_SEGMENTS) {
                val straightSegments = featureSegments.asSequence().dropLast(nCurvedSegmentsPresent)
                features.add(
                    Feature.Straight(
                        featureSegments.first().startsAtDistance,
                        straightSegments.sumOf { it.arcLength })
                )
                repeat(featureSegments.size - nCurvedSegmentsPresent) {
                    featureSegments.removeFirst()
                }
                inCorner = true
            }
        }
    }

    if (inCorner) {
        if (featureSegments.size >= CORNER_MIN_SEGMENTS) {
            features.add(Feature.Corner(featureSegments.first().startsAtDistance, featureSegments.toMutableList()))
        } else {
            features.add(
                Feature.Straight(
                    featureSegments.first().startsAtDistance,
                    featureSegments.sumOf { it.arcLength })
            )
        }
    } else {
        features.add(Feature.Straight(featureSegments.first().startsAtDistance, featureSegments.sumOf { it.arcLength }))
    }

    if (features.size > 1 && features[features.lastIndex] is Feature.Straight && features[features.lastIndex - 1] is Feature.Straight) {
        val s2 = features.removeLast() as Feature.Straight
        val s1 = features.removeLast() as Feature.Straight
        features.add(Feature.Straight(s1.startsAtTrackDistance, s1.distance + s2.distance))
    }

    return features
}

sealed interface Feature {
    val startsAtTrackDistance: Double

    class Straight(
        override val startsAtTrackDistance: Double,
        val distance: Double,
    ) : Feature

    class Corner(
        override val startsAtTrackDistance: Double,
        val segments: List<TrackSegment>,
    ) : Feature {
        val totalAngle: Double = segments.sumOf { it.angleToNext }
        val totalDistance: Double by lazy { segments.sumOf { it.arcLength } }

        val direction get() = if (totalAngle > 0) Direction.RIGHT else Direction.LEFT

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

private fun cornerFeatureToPacenoteItem(corner: Feature.Corner): PacenoteItem {
    val sections = findCornerSections(corner).map { it.toPacenote() }
    return PacenoteItem.Corner(corner.direction, false, sections)
}

private fun findCornerSections(corner: Feature.Corner): List<TmpCornerSection> {
    val significantSegments = corner.segments
    val radii = averageRadii(significantSegments, RADIUS_CHANGE_SMOOTHING_DISTANCE)
    val radiiDerivatives = radii.derivative()
    val stableRadiusSections = radiiDerivatives
        .findRunsAtYValue(0.0, 2.0)
        .filterNot { (startsAt, endsAt) -> startsAt == endsAt }
        .toList()
    if (stableRadiusSections.count() <= 1) {
        return listOf(TmpCornerSection.fromTrackSegments(significantSegments))
    }

    val mergedSections = stableRadiusSections
        .asSequence()
        .map { (firstX, lastX) ->
            val indexOfFirst = significantSegments.indexOfFirst { it.startsAtDistance == firstX }
            val indexOfLast = significantSegments.indexOfFirst { it.startsAtDistance == lastX }
            significantSegments.subList(indexOfFirst, indexOfLast + 1)
        }
        .map(TmpCornerSection::fromTrackSegments)
        .mergeConsecutiveSameSeverity()
        .toList()

    val trimmedSections = if (mergedSections.size <= 1) {
        mergedSections
    } else {
        mergedSections.dropFirstAndLastWhile { it.severity >= PacenoteItem.Corner.Severity.SIX && it.length < 10.0 }
    }

    return trimmedSections
}

private fun Sequence<TmpCornerSection>.mergeConsecutiveSameSeverity(): Sequence<TmpCornerSection> {
    return sequence {
        var currentSection = first()
        for (section in drop(1)) {
            if (section.severity == currentSection.severity) {
                currentSection += section
            } else {
                yield(currentSection)
                currentSection = section
            }
        }
        yield(currentSection)
    }
}

private data class TmpCornerSection(
    val radius: Double,
    val severity: PacenoteItem.Corner.Severity,
    val length: Double,
) {
    operator fun plus(other: TmpCornerSection): TmpCornerSection {
        return TmpCornerSection(
            (radius * length + other.radius * other.length) / (length + other.length),
            severity,
            length + other.length
        )
    }

    fun toPacenote(): PacenoteItem.Corner.Section {
        return PacenoteItem.Corner.Section(severity, length, emptyList(), radius)
    }

    companion object {
        fun fromTrackSegments(section: List<TrackSegment>): TmpCornerSection {
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

            return TmpCornerSection(radius, severity, section.sumOf { it.arcLength })
        }
    }
}

private fun averageRadii(segments: Iterable<TrackSegment>, acrossMeters: Double): Sequence<Pair<Double, Double>> {
    return sequence {
        val currentWindowSequence = ArrayDeque<TrackSegment>((acrossMeters * 2.0).toInt())
        var distanceInWindow = 0.0
        var yielded = false
        suspend fun SequenceScope<Pair<Double, Double>>.yieldCurrent() {
            val totalLength = currentWindowSequence.sumOf { it.arcLength }
            val averageRadius = currentWindowSequence.sumOf { it.radiusToNext * it.arcLength } / totalLength
            yield(
                Pair(
                    currentWindowSequence.first().startsAtDistance,
                    averageRadius
                )
            )
            val removedSegment = currentWindowSequence.removeFirst()
            distanceInWindow -= removedSegment.arcLength
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
        if (!yielded) {
            yieldCurrent()
        }
    }
}

private fun Sequence<Pair<Double, Double>>.derivative(): Sequence<Pair<Double, Double>> {
    return zipWithNext { a, b -> Pair(a.first, (b.second - a.second) / (b.first - a.first)) }
}

private fun Sequence<Pair<Double, Double>>.findRunsAtYValue(
    yValue: Double,
    tolerance: Double
): Sequence<Pair<Double, Double>> {
    val targetRange = yValue - tolerance..yValue + tolerance
    return sequence {
        var inRun = false
        var runStartedAtX = 0.0
        var previousX = 0.0
        for ((x, y) in this@findRunsAtYValue) {
            val yInRange = y in targetRange
            if (inRun && !yInRange) {
                yield(Pair(runStartedAtX, previousX))
                inRun = false
            } else if (!inRun && yInRange) {
                runStartedAtX = x
                inRun = true
            }
            previousX = x
        }
    }
}

private val List<TrackSegment>.compoundRadius: Double
    get() {
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
            val severity: Severity,
            val length: Double,
            val modifiers: List<Modifier>,
            val radius: Double,
        ) {
            override fun toString() = toString(null, true)

            fun toString(
                withDirection: Feature.Corner.Direction?,
                includeSeverity: Boolean,
            ): String {
                val sb = StringBuilder()
                if (includeSeverity) {
                    sb.append(severity.toString())
                }
                sb.append("(r=")
                sb.append(radius.toInt())
                sb.append("m,d=")
                sb.append(length.toInt())
                sb.append("m)")
                if (withDirection != null) {
                    sb.append(' ')
                    sb.append(withDirection.toString())
                }
                for (mod in modifiers) {
                    sb.append(' ')
                    sb.append(mod.toString())
                }
                return sb.toString()
            }
        }

        override fun toString(): String {
            val sb = StringBuilder()
            if (isAtJunction) {
                sb.append("turn ")
            }
            sb.append(sections.first().toString(direction, includeSeverity = true))
            var currentSeverity = sections.first().severity
            for (section in sections.drop(1)) {
                sb.append(' ')
                val severityChange = currentSeverity.compareTo(section.severity)
                when {
                    severityChange < 0 -> {
                        sb.append("opens ")
                    }

                    severityChange > 0 -> {
                        sb.append("tightens ")
                    }
                }
                sb.append(
                    section.toString(
                        null,
                        includeSeverity = severityChange >= 0 || section.severity < Severity.SIX
                    )
                )
                currentSeverity = section.severity
            }
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

    /**
     * Additional information applicable to _any_ stretch of road.
     */
    interface SectionModifier {
        data object OverCrest : SectionModifier
    }
}