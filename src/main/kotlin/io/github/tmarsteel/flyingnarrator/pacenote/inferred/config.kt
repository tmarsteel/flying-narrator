package io.github.tmarsteel.flyingnarrator.pacenote.inferred

import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters

/**
 * Straight distances are rounded to _the next lower_ multiple of this value. E.g., `10` for 80, 90, 100, 110, 120.
 * Given `10`, `89` is reported as `80`; given `5`, `89` is reported as `85`.
 */
val ROUND_STRAIGHT_DISTANCES_TO_MULTIPLE_OF = 10.meters

/**
 * Straight sections with a length equal to or less than this (after rounding) value will be elided:
 * At the start and end of the stage, they're simply dropped, between corners they are replaced with [InferredPacenoteItem.ShortTransition]
 */
val STRAIGHT_ELISION_DISTANCE_THRESHOLD = 20.0.meters

/**
 * Straight sections between two corners that are shorter than this distance will be replaced with
 * [InferredPacenoteItem.ImmediateTransition] (instead of [InferredPacenoteItem.ShortTransition])
 */
val IMMEDIATE_TRANSITION_DISTANCE_THRESHOLD = 10.0.meters

/**
 * Corners with an overall radius less than this can be considered "square"
 */
val SQUARE_MAX_COMPOUND_RADIUS = 35.0.meters

/**
 * If a corner has a section with a radius smaller than this, it can be considered "square"
 */
val SQUARE_MAX_RADIUS = 10.0.meters

/**
 * If the length of track at a radius of [SQUARE_MAX_RADIUS] is this length or more, the corner can be considered "square".
 */
const val SQUARE_CORNER_MIN_DISTANCE = 3.0

/**
 * If the [io.github.tmarsteel.flyingnarrator.feature.Feature.Corner.totalAngle] of a corner is in this range, it can be considered "square".
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
 * Corner sections that change the radius faster than this number of radius-meters/track-meters, it can be considered
 * opening / tightening.
 */
const val SEVERITY_CHANGE_DRADIUS_THRESHOLD = 1.5

/**
 * If a corner has a total angle in this range, it is reported as a hairpin
 */
val HAIRPIN_TOTAL_ANGLE_RANGE = Math.toRadians(135.0)..Math.toRadians(225.0)

/**
 * Corners with a total angle of [HAIRPIN_TOTAL_ANGLE_RANGE] but a severity higher than [HAIRPIN_MAX_SEVERITY]
 * will not be abbreviated to [InferredPacenoteItem.Hairpin].
 */
val HAIRPIN_MAX_SEVERITY = InferredPacenoteItem.Corner.Severity.THREE