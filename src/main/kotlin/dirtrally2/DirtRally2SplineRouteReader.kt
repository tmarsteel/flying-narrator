package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.HermiteSpline
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.RouteReader
import io.github.tmarsteel.flyingnarrator.Vector3
import io.github.tmarsteel.flyingnarrator.zipWithNextAndEmitLast
import tools.jackson.databind.MapperFeature
import tools.jackson.dataformat.xml.XmlFactory
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.jaxb.JaxbAnnotationModule
import tools.jackson.module.kotlin.kotlinModule
import java.nio.file.Path

class DirtRally2SplineRouteReader(
    val splineDto: DR2TrackSplines,
) : RouteReader {
    constructor(xmlSource: Path) : this(
        objectMapper.readValue(xmlSource, DR2TrackSplines::class.java)
    )

    val routeCentralSpline by lazy {
        splineDto.centralSplineOriginal.splines.asSequence()
            .zipWithNextAndEmitLast(
                zipMapper = this::withoutOverlap,
                mapLast = { it.controlPoints },
            )
            .flatten()
            .map { cp -> HermiteSpline.ControlPoint(
                Vector3(cp.z, cp.x, cp.y),
                Vector3(cp.forwardZ, cp.forwardX, cp.forwardY),
            ) }
            .toList()
    }

    private val route by lazy {
        routeCentralSpline
            .asSequence()
            .zipWithNextAndEmitLast(
                zipMapper = { a, b ->
                    val distance = (b.position - a.position).length2d
                    val step = TARGET_MAX_SEGMENT_LENGTH_METERS / distance
                    var t = step
                    sequence {
                        yield(a.position)
                        while (t < 1.0) {
                            yield(HermiteSpline.interpolate(a, b, t))
                            t += step
                        }
                    }
                },
                mapLast = { sequenceOf(it.position) },
            )
            .flatten()
            .zipWithNext { pos1, pos2 ->
                pos2 - pos1
            }
            .toList()
    }

    /**
     * In the game data, sometimes controlpoints between two adjacent `<spline>`s overlap (e.g. in germany/route_0,
     * `<CentralSplineOriginal>` the second `<spline>` repeats the last 4 control points of the first `<spline>`).
     *
     * @return a view of the [DR2TrackSplineControlPoint]s from [splineDto] except those that are
     * also contained in [nextSpline].
     */
    private fun withoutOverlap(splineDto: DR2TrackSpline, nextSplineDto: DR2TrackSpline): List<DR2TrackSplineControlPoint> {
        val idxOfOverlapEndInNext = nextSplineDto.controlPoints.indexOf(splineDto.controlPoints.last())
        if (idxOfOverlapEndInNext == -1) {
            return splineDto.controlPoints
        }

        for (idxInNext in idxOfOverlapEndInNext - 1 downTo 0) {
            if (splineDto.controlPoints[splineDto.controlPoints.size - idxOfOverlapEndInNext - 1 + idxInNext] != nextSplineDto.controlPoints[idxInNext]) {
                // overlap is not perfect
                throw DirtRally2RouteReadingException(
                    "[WARN] non-perfect overlap detected between splines; overlapping control point: ${nextSplineDto.controlPoints[idxOfOverlapEndInNext]}"
                )
            }
        }

        // overlap proven identical
        return splineDto.controlPoints.subList(0, splineDto.controlPoints.size - idxOfOverlapEndInNext - 1)
    }

    override fun read(): Route {
        return route
    }

    private companion object {
        private const val TARGET_MAX_SEGMENT_LENGTH_METERS = 1.0
        val objectMapper: XmlMapper = XmlMapper.Builder(XmlFactory())
            .addModule(kotlinModule())
            .addModule(JaxbAnnotationModule())
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build()
    }
}

