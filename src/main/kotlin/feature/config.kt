package io.github.tmarsteel.flyingnarrator.feature

/**
 * The [TrackSegment.radiusToNext] is capped to this value
 */
const val MAX_REPORTED_RADIUS = 750.0

/**
 * The minimum [compoundRadius] for a part of the route to be considered a straight section
 */
const val STRAIGHTISH_MIN_RADIUS = 400.0

/**
 * The radii measured throughout a corner will be average across a distance of [CORNER_RADIUS_AVERAGE_WINDOW_SIZE]
 * meters before analyzing for severities
 */
const val CORNER_RADIUS_AVERAGE_WINDOW_SIZE = 7.5

/**
 * [TrackSegment.turnyness] absolute less than this is considered straight
 */
const val CORNER_SEVERITY_THRESHOLD = Double.MIN_VALUE

/**
 * Corner extension will extend corners so that the angle between the corner exit and the following corner entry
 * is this or less.
 */
val CORNER_EXTENSION_STRAIGHTISH_ANGLE: Double = Math.toRadians(2.5)

/**
 * Corner extension will extend corners no further than this amount of meters to make the [CORNER_EXTENSION_STRAIGHTISH_ANGLE]
 * work out.
 */
const val CORNER_EXTENSION_MAX_DISTANCE = 10.0