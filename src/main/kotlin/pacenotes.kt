package io.github.tmarsteel.flyingnarrator

import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * Straight distances are rounded to this amount, e.g. `10` for 300, 310, 320, 330, ...
 */
val ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF = 10

/**
 * On corners that have little detail in the input data, it is assumed that turns extend at most
 * this distance into the straight sections before/after
 */
val MAX_CORNER_ROUNDING_DISTANCE = 20.0

/**
 * Two [RoadSegment]s that have a radius greater than this value will be considered straight
 */
val STRAIGHTISH_RADIUS_THRESHOLD = 200.0

/**
 * The [TrackSegment.radiusToNext] is capped to this value
 */
val MAX_REPORTED_RADIUS = STRAIGHTISH_RADIUS_THRESHOLD * 10.0

/**
 * Straight sections with a length equal to or less than this (after rounding) value will be elided:
 * At the start and end of the stage, they're simply dropped, between corners they are replaced with [PacenoteItem.ShortTransition]
 */
val STRAIGHT_ELISION_DISTANCE_THRESHOLD = 20.0

fun Sequence<TrackSegment>.derivePacenotes(): List<PacenoteItem> {
    val features = detectFeatures()

    val pacenoteItems = mutableListOf<PacenoteItem>()
    for (feature in features) {
        when (feature) {
            is Feature.Straight -> {
                val distance = (feature.distance.toInt() / ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF) * ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF
                if (distance > STRAIGHT_ELISION_DISTANCE_THRESHOLD) {
                    pacenoteItems += PacenoteItem.Straight(distance)
                } else if (!pacenoteItems.isEmpty()) {
                    pacenoteItems += PacenoteItem.ShortTransition
                }
            }
            is Feature.Turn -> {
                if (pacenoteItems.lastOrNull() is PacenoteItem.Turn) {
                    pacenoteItems += PacenoteItem.ImmediateTransition
                }
                pacenoteItems += turnFeatureToPacenoteItem(feature)
            }
        }
    }

    while (pacenoteItems.lastOrNull() is PacenoteItem.Transition) {
        pacenoteItems.removeLast()
    }

    return pacenoteItems
}

data class TrackSegment(
    val roadSegment: RoadSegment,
    val radiusToNext: Double,
    val angleToNext: Double,
)

fun Sequence<TrackSegment>.detectFeatures(): List<Feature> {
    var state: FeatureDetectionState = FeatureDetectionState.Straightish.startStraightish(this.first(), 0)
    val features = mutableListOf<Feature>()
    for ((index, segment) in this.withIndex().drop(1)) {
        val (nextState, segmentFeatures) = state.traverse(segment, index)
        features += segmentFeatures
        state = nextState
    }
    features += state.finish()

    features.sortBy { it.startsAtSegmentIndex }
    return features
}

/**
 * Will split [RoadSegment]s longer than [maxLength] into two or three new [RoadSegment]s, such that the two ends
 * are at most [maxLength] long ([Vector3.length2d]).
 */
private fun Sequence<RoadSegment>.capAngledSegmentLength2d(maxLength: Double): Sequence<RoadSegment> {
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

fun Route.trackSegments(): Sequence<TrackSegment> {
    return this
        .asSequence()
        .capAngledSegmentLength2d(MAX_CORNER_ROUNDING_DISTANCE)
        .windowed(size = 2, step =1, partialWindows = true)
        .mapIndexed { index, segments ->
            if (segments.size == 1) {
                return@mapIndexed TrackSegment(segments.single(), MAX_REPORTED_RADIUS, 0.0)
            }
            val (a, b) = segments
            val radius = radiusOfCorner(a, b).coerceAtMost(MAX_REPORTED_RADIUS)
            val angle = a.angleTo(b)
            TrackSegment(a, radius, angle)
        }
}

/**
 * @return the radius of a circle that passes through the points [Vector3.ORIGIN], [base] and `base + turn`. Returns
 * [Double.POSITIVE_INFINITY] if the angle between [base] and [turn] is `0`.
 */
private fun radiusOfCorner(base: Vector3, turn: Vector3): Double {
    val line1 = MLine(Vector3.ORIGIN, base.rotate2d90degCounterClockwise())
    val line2 = MLine(base + turn, turn.rotate2d90degCounterClockwise())

    val center = line1.intersect2d(line2)
        ?: return Double.POSITIVE_INFINITY

    return center.length2d
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

sealed interface FeatureDetectionState {
    val currentSegment: TrackSegment

    fun traverse(segment: TrackSegment, segmentIndex: Int): Pair<FeatureDetectionState, List<Feature>>
    fun finish(): List<Feature>

    class Straightish(
        override val currentSegment: TrackSegment,
        val straightishDistance: Double,
        val straightStartedAtIndex: Int,
    ) : FeatureDetectionState {
        override fun finish(): List<Feature> {
            return listOf(Feature.Straight(straightStartedAtIndex, straightishDistance))
        }

        override fun traverse(
            segment: TrackSegment,
            segmentIndex: Int
        ): Pair<FeatureDetectionState, List<Feature>> {
            if (segment.radiusToNext <= STRAIGHTISH_RADIUS_THRESHOLD) {
                val nextState = InTurn(segmentIndex, segmentIndex, this, segment)
                return Pair(nextState, finish())
            }

            return Pair(
                Straightish(segment, straightishDistance + segment.roadSegment.length(), segmentIndex),
                emptyList(),
            )
        }

        companion object {
            fun startStraightish(segment: TrackSegment, startIndex: Int): Straightish = Straightish(segment, segment.roadSegment.length(), startIndex)
        }
    }

    class InTurn(
        val turnStartsAtIndex: Int,
        val currentIndex: Int,
        val previousState: FeatureDetectionState,
        override val currentSegment: TrackSegment,
    ) : FeatureDetectionState {
        private fun collectTurnSegments(): List<InTurn> {
            val states = mutableListOf<InTurn>()
            var state: FeatureDetectionState = this
            while (state is InTurn) {
                states.add(state)
                if (state.turnStartsAtIndex == state.currentIndex) {
                    break
                }
                state = state.previousState
            }
            states.reverse()
            return states
        }

        override fun finish(): List<Feature> {
            return listOf(Feature.Turn(turnStartsAtIndex, collectTurnSegments()))
        }

        override fun traverse(
            segment: TrackSegment,
            segmentIndex: Int
        ): Pair<FeatureDetectionState, List<Feature>> {
            if (previousState is InTurn && previousState.currentSegment.radiusToNext > STRAIGHTISH_RADIUS_THRESHOLD) {
                val nextState = Straightish.startStraightish(segment, segmentIndex)
                return Pair(nextState, finish())
            }

            if (this.currentSegment.angleToNext.sign == segment.angleToNext.sign) {
                // turn continues
                return Pair(
                    InTurn(turnStartsAtIndex, segmentIndex, this, segment),
                    emptyList(),
                )
            } else {
                // s-curve
                return Pair(
                    InTurn(segmentIndex, segmentIndex, this, segment),
                    finish()
                )
            }
        }
    }
}

sealed interface Feature {
    val startsAtSegmentIndex: Int

    class Straight(
        override val startsAtSegmentIndex: Int,
        val distance: Double,
    ) : Feature

    class Turn(
        override val startsAtSegmentIndex: Int,
        val states: List<FeatureDetectionState.InTurn>,
    ) : Feature {
        val totalAngle: Double = states.sumOf { it.currentSegment.angleToNext }
        val totalDistance: Double by lazy { states.map { it.currentSegment.roadSegment.length() }.reduce(Double::plus) }

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

private fun radiusToSeverity(radius: Double): PacenoteItem.Turn.Severity {
    return when (radius) {
        in 0.0..5.0 -> PacenoteItem.Turn.Severity.SQUARE
        in 5.0..10.0 -> PacenoteItem.Turn.Severity.ONE
        in 10.0..20.0 -> PacenoteItem.Turn.Severity.TWO
        in 20.0..40.0 -> PacenoteItem.Turn.Severity.THREE
        in 40.0..80.0 -> PacenoteItem.Turn.Severity.FOUR
        in 80.0 .. 120.0 -> PacenoteItem.Turn.Severity.FIVE
        else -> PacenoteItem.Turn.Severity.SIX
    }
}

private fun turnFeatureToPacenoteItem(turn: Feature.Turn): PacenoteItem {
    val modifiers = mutableListOf<PacenoteItem.Turn.Modifier>()
    when (turn.totalDistance) {
        in 0.0..50.0 -> { /* no modifier */ }
        in 50.0 .. 100.0 -> modifiers.add(PacenoteItem.Turn.Modifier.Length(
            PacenoteItem.Turn.Modifier.Length.Value.LONG,
        ))
        in 100.0 .. 150.0 -> modifiers.add(PacenoteItem.Turn.Modifier.Length(
            PacenoteItem.Turn.Modifier.Length.Value.EXTRA_LONG,
        ))
        else -> modifiers.add(PacenoteItem.Turn.Modifier.Length(
            PacenoteItem.Turn.Modifier.Length.Value.EXTRA_EXTRA_LONG,
        ))
    }

    val initialSeverity = radiusToSeverity(turn.states.first().currentSegment.radiusToNext)
    var carrySeverity = initialSeverity
    for (turnState in turn.states.drop(1)) {
        val segmentSeverity = radiusToSeverity(turnState.currentSegment.radiusToNext)
        when {
            segmentSeverity == carrySeverity -> { /* continues */ }
            segmentSeverity > carrySeverity -> {
                val prevModifier = modifiers.lastOrNull()
                if (prevModifier is PacenoteItem.Turn.Modifier.Opens) {
                    modifiers.removeLast()
                    modifiers.add(PacenoteItem.Turn.Modifier.Opens(prevModifier.toSeverity.coerceAtLeast(segmentSeverity)))
                } else {
                    modifiers.add(PacenoteItem.Turn.Modifier.Opens(segmentSeverity))
                }
            }
            segmentSeverity < carrySeverity -> {
                val prevModifier = modifiers.lastOrNull()
                if (prevModifier is PacenoteItem.Turn.Modifier.Tightens) {
                    modifiers.removeLast()
                    modifiers.add(PacenoteItem.Turn.Modifier.Tightens(prevModifier.toSeverity.coerceAtMost(segmentSeverity)))
                } else {
                    modifiers.add(PacenoteItem.Turn.Modifier.Tightens(segmentSeverity))
                }
            }
        }

        carrySeverity = segmentSeverity
    }

    return PacenoteItem.Turn(initialSeverity, turn.direction, modifiers)
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
    data class Turn(
        val initialSeverity: Severity,
        val direction: Feature.Turn.Direction,
        val modifiers: List<Modifier>,
    ) : PacenoteItem {
        override fun toString(): String {
            var str = "$initialSeverity $direction"
            modifiers.forEach { modifier ->
                str += " $modifier"
            }

            return str
        }

        sealed interface Modifier {
            data class Opens(val toSeverity: Severity) : Modifier {
                override fun toString(): String {
                    return "opens $toSeverity"
                }
            }
            data class Tightens(val toSeverity: Severity) : Modifier {
                override fun toString(): String {
                    return "tightens $toSeverity"
                }
            }
            data class Length(val length: Value) : Modifier {
                override fun toString() = length.toString()

                enum class Value {
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
            ;

            override fun toString(): String {
                return name.lowercase()
            }
        }
    }
}
