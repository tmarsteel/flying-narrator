package io.github.tmarsteel.flyingnarrator.dirtrally2

import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

/**
 * Model for the `track_spline.xml` file - found in the `*.nefs` files in `$GAME_DIR/locations`, at sub-path
 * `tracks/locations/$location/$location_rally_$number/route_$stageNumber/track_spline.xml`.
 */
@XmlRootElement
class DR2TrackSplines(
    @XmlAttribute
    val version: Int,

    /**
     * This spline follows the center of the racetrack
     */
    @XmlElement
    val centralSplineOriginal: DR2TrackSplineSet,

    /*
     * The maxDeformed splines seem identical to the original splines (as observed in `germany_01/route_0` and `usa_01/route_3`),
     * their purpose is currently unclear.
     */

    /**
     * Follows the left edge of what usually seems to be the usable racetrack, not the track limits; left from the perspective of racing direction.
     */
    @XmlElement
    val leftSplineOriginal: DR2TrackSplineSet,

    /**
     * Same as [leftSplineOriginal], but to the right of the centerline.
     */
    @XmlElement
    val rightSplineOriginal: DR2TrackSplineSet,

    /**
     * A spline between [centralSplineOriginal] and [leftSplineOriginal]
     */
    @XmlElement
    val centreLeftSplineOriginal: DR2TrackSplineSet,

    /**
     * A spline between [centralSplineOriginal] and [rightSplineOriginal]
     */
    @XmlElement
    val centreRightSplineOriginal: DR2TrackSplineSet,
)