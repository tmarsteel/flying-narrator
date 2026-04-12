package io.github.tmarsteel.flyingnarrator.dirtrally2

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.tmarsteel.flyingnarrator.HermiteSpline
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.RouteReader
import io.github.tmarsteel.flyingnarrator.Vector3
import tools.jackson.databind.MapperFeature
import tools.jackson.dataformat.xml.XmlFactory
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import tools.jackson.module.jaxb.JaxbAnnotationModule
import tools.jackson.module.kotlin.kotlinModule
import java.nio.file.Path
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

class DirtRally2SplineRouteReader(
    val splines: DR2TrackSplines,
) : RouteReader {
    constructor(xmlSource: Path) : this(
        objectMapper.readValue(xmlSource, DR2TrackSplines::class.java)
    )

    private val route by lazy {
        splines.centralSplineOriginal.splines.asSequence()
            .flatMap { splineDto ->
                splineDto.controlPoints.asSequence()
                    .map { cp -> HermiteSpline.ControlPoint(
                        Vector3(cp.z, cp.x, cp.y),
                        Vector3(cp.forwardZ, cp.forwardX, cp.forwardY),
                    ) }
                    .zipWithNext { a, b ->
                        val distance = (b.position - a.position).length2d
                        val step = 1.0 / distance
                        var t = step
                        sequence {
                            yield(a.position)
                            while (t < 1.0) {
                                yield(HermiteSpline.interpolate(a, b, t))
                                t += step
                            }
                        }
                    }
                    .flatten()
                    .zipWithNext { pos1, pos2 ->
                        pos2 - pos1
                    }
            }
            .toList()
    }

    override fun read(): Route {
        return route
    }

    companion object {
        val objectMapper = XmlMapper.Builder(XmlFactory())
            .addModule(kotlinModule())
            .addModule(JaxbAnnotationModule())
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build()
    }
}

@XmlRootElement
class DR2TrackSplines(
    @XmlAttribute
    val version: Int,
    
    @XmlElement
    val centralSplineOriginal: DR2TrackSplineSet,

    @XmlElement
    val centralSplineMaxDeformed: DR2TrackSplineSet,

    @XmlElement
    val leftSplineOriginal: DR2TrackSplineSet,

    @XmlElement
    val leftSplineMaxDeformed: DR2TrackSplineSet,

    @XmlElement
    val rightSplineOriginal: DR2TrackSplineSet,

    @XmlElement
    val rightSplineMaxDeformed: DR2TrackSplineSet,

    @XmlElement
    val centreLeftSplineOriginal: DR2TrackSplineSet,

    @XmlElement
    val centreLeftSplineMaxDeformed: DR2TrackSplineSet,

    @XmlElement
    val centreRightSplineOriginal: DR2TrackSplineSet,

    @XmlElement
    val centreRightSplineMaxDeformed: DR2TrackSplineSet,
)

class DR2TrackSplineSet(
    @XmlElement
    @JsonProperty("spline")
    @JacksonXmlElementWrapper(useWrapping = false)
    val splines: List<DR2TrackSpline>
)

class DR2TrackSpline(
    @XmlElement
    @JsonProperty("cp")
    @JacksonXmlElementWrapper(useWrapping = false)
    val controlPoints: List<DR2TrackSplineControlPoint>,
)

class DR2TrackSplineControlPoint(
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

    @XmlAttribute
    @JsonProperty("upX")
    val upwardsX: Double,

    @XmlAttribute
    @JsonProperty("upY")
    val upwardsY: Double,

    @XmlAttribute
    @JsonProperty("upZ")
    val upwardsZ: Double,
)