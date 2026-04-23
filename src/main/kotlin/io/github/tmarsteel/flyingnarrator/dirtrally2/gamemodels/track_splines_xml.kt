package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReadingException
import io.github.tmarsteel.flyingnarrator.geometry.HermiteSpline
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.utils.zipWithNextAndEmitLast
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
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

class DR2TrackSplineSet(
    @XmlElement
    @JsonProperty("spline")
    @JacksonXmlElementWrapper(useWrapping = false)
    val splines: List<DR2TrackSpline>
) {
    val controlPoints: Sequence<HermiteSpline.ControlPoint> = splines
        .asSequence()
        .zipWithNextAndEmitLast(
            zipMapper = this::withoutOverlap,
            mapLast = { it.controlPoints },
        )
        .flatten()
        .map { cp -> HermiteSpline.ControlPoint(
            Vector3(cp.z, cp.x, cp.y),
            Vector3(cp.forwardZ, cp.forwardX, cp.forwardY),
        ) }

    /**
     * In the game data, sometimes controlpoints between two adjacent `<spline>`s overlap (e.g. in germany/route_0,
     * `<CentralSplineOriginal>` the second `<spline>` repeats the last 4 control points of the first `<spline>`).
     *
     * @return a view of the [DR2TrackSplineControlPoint]s from [splineDto] except those that are
     * also contained in [nextSpline].
     */
    private fun withoutOverlap(splineDto: DR2TrackSpline, nextSplineDto: DR2TrackSpline): List<DR2TrackSplineControlPoint> {
        val idxOfOverlapEndInNext = nextSplineDto.controlPoints.indexOfFirst {
            it.positionEquals(splineDto.controlPoints.last(), tolerance = OVERLAP_MATCHING_TOLERANCE)
        }
        if (idxOfOverlapEndInNext == -1) {
            return splineDto.controlPoints
        }

        for (idxInNext in idxOfOverlapEndInNext - 1 downTo 0) {
            val controlPointInCurrent = splineDto.controlPoints[splineDto.controlPoints.size - idxOfOverlapEndInNext - 1 + idxInNext]
            val controlPointInNext = nextSplineDto.controlPoints[idxInNext]
            if (!controlPointInCurrent.positionEquals(controlPointInNext, tolerance = OVERLAP_MATCHING_TOLERANCE)) {
                // overlap is not perfect
                throw DirtRally2RouteReadingException(
                    "[WARN] non-perfect overlap detected between splines; overlapping control point: ${nextSplineDto.controlPoints[idxOfOverlapEndInNext]}"
                )
            }
        }

        // overlap proven identical
        return splineDto.controlPoints.subList(0, splineDto.controlPoints.size - idxOfOverlapEndInNext - 1)
    }

    companion object {
        const val OVERLAP_MATCHING_TOLERANCE = 1.0
    }
}

data class DR2TrackSplineControlPoint(
    @XmlAttribute
    @JsonProperty("posX")
    val x: Double,

    @XmlAttribute
    @JsonProperty("posY")
    val y: Double,

    @XmlAttribute
    @JsonProperty("posZ")
    val z: Double,

    @XmlAttribute
    @JsonProperty("forX")
    val forwardX: Double,

    @XmlAttribute
    @JsonProperty("forY")
    val forwardY: Double,

    @XmlAttribute
    @JsonProperty("forZ")
    val forwardZ: Double,

    /*
     * The upX, upY and upZ should, theoretically, describe a normal vector onto the track giving information
     * about track inclination. As observed in `germany_01/route_0` and `usa_01/route_3`, this vector is always
     * (0, 1, 0) though (so always pointing straight up).
     */

    @XmlAttribute
    @JsonProperty("upX")
    val upwardsX: Double,

    @XmlAttribute
    @JsonProperty("upY")
    val upwardsY: Double,

    @XmlAttribute
    @JsonProperty("upZ")
    val upwardsZ: Double,
) {
    fun positionEquals(other: DR2TrackSplineControlPoint, tolerance: Double): Boolean {
        val deltaX = other.x - this.x
        val deltaY = other.y - this.y
        val deltaZ = other.z - this.z
        val deltaDistance = Vector3(deltaX, deltaY, deltaZ).length
        return deltaDistance <= tolerance
    }

    override fun toString(): String {
        return """
            <cp posX="$x" posY="$y" posZ="$z" forX="$forwardX" forY="$forwardY" forZ="$forwardZ" upX="$upwardsX" upY="$upwardsY" upZ="$upwardsZ" /> 
        """.trimIndent()
    }
}

class DR2TrackSpline(
    @XmlElement
    @JsonProperty("cp")
    @JacksonXmlElementWrapper(useWrapping = false)
    val controlPoints: List<DR2TrackSplineControlPoint>,
)