package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.HermiteSpline
import io.github.tmarsteel.flyingnarrator.RoadSegment
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.RouteReader
import io.github.tmarsteel.flyingnarrator.feature.OPTIMAL_ROAD_SEGMENT_LENGTH
import tools.jackson.databind.MapperFeature
import tools.jackson.dataformat.xml.XmlFactory
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.jaxb.JaxbAnnotationModule
import tools.jackson.module.kotlin.kotlinModule
import java.nio.file.Path

class DirtRally2RouteReader(
    val splineDto: DR2TrackSplines,
    val progressDto: DR2ProgressTrackData,
) : RouteReader {
    constructor(sourceDir: Path) : this(
        objectMapper.readValue(sourceDir.resolve("track_spline.xml"), DR2TrackSplines::class.java),
        objectMapper.readValue(sourceDir.resolve("progress_track.xml"), DR2ProgressTrackData::class.java),
    )

    private val progressRoute = progressDto.routes.single()
    private val startSplit = progressRoute.splits.single { it.type == DR2ProgressRouteSplit.Type.START }
    private val finishSplit = progressRoute.splits.single { it.type == DR2ProgressRouteSplit.Type.FINISH }
    val startsAtDistance = progressDto.gates.single { it.id == startSplit.gateId }.distance
    val finishesAtDistance = progressDto.gates.single { it.id == finishSplit.gateId }.distance
    init {
        check(startsAtDistance < finishesAtDistance)
    }

    val positionsOnCentralSpline = HermiteSpline.interpolate(
        splineDto.centralSplineOriginal.controlPoints,
        OPTIMAL_ROAD_SEGMENT_LENGTH,
    )

    private val route by lazy {
        val allVectors = positionsOnCentralSpline
            .zipWithNext { pos1, pos2 ->
                pos2 - pos1
            }
            .toMutableList()

        var idxBeforeStart = 0
        var idxAfterFinish = 0
        var distanceCarry = 0.0
        for ((idx, vec) in allVectors.withIndex()) {
            distanceCarry += vec.length
            if (distanceCarry < startsAtDistance) {
                idxBeforeStart = idx
            }
            if (distanceCarry >= finishesAtDistance) {
                idxAfterFinish = idx + 1
                break
            }
        }

        allVectors
            .subList(idxBeforeStart, idxAfterFinish)
            .map(::RoadSegment)
    }

    override fun read(): Route {
        return route
    }

    private companion object {
        val objectMapper: XmlMapper = XmlMapper.Builder(XmlFactory())
            .addModule(kotlinModule())
            .addModule(JaxbAnnotationModule())
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build()
    }
}