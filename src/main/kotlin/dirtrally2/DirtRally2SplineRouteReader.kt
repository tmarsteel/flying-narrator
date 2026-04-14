package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.HermiteSpline
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.RouteReader
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

    val positionsOnCentralSpline = HermiteSpline.interpolate(
        splineDto.centralSplineOriginal.controlPoints,
        TARGET_MAX_SEGMENT_LENGTH_METERS,
    )

    private val route by lazy {
        positionsOnCentralSpline
            .zipWithNext { pos1, pos2 ->
                pos2 - pos1
            }
            .toList()
    }





    override fun read(): Route {
        return route
    }

    private companion object {
        private const val TARGET_MAX_SEGMENT_LENGTH_METERS = 5.0
        val objectMapper: XmlMapper = XmlMapper.Builder(XmlFactory())
            .addModule(kotlinModule())
            .addModule(JaxbAnnotationModule())
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build()
    }
}