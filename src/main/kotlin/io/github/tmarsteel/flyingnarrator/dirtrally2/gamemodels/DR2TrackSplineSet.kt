package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.tmarsteel.flyingnarrator.HermiteSpline
import io.github.tmarsteel.flyingnarrator.Vector3
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReadingException
import io.github.tmarsteel.flyingnarrator.zipWithNextAndEmitLast
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import javax.xml.bind.annotation.XmlElement

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