package io.github.tmarsteel.flyingnarrator

import de.micromata.opengis.kml.v_2_2_0.Coordinate
import de.micromata.opengis.kml.v_2_2_0.Document
import de.micromata.opengis.kml.v_2_2_0.Kml
import de.micromata.opengis.kml.v_2_2_0.LineString
import de.micromata.opengis.kml.v_2_2_0.Placemark
import io.github.tmarsteel.flyingnarrator.feature.OPTIMAL_ROAD_SEGMENT_LENGTH
import jakarta.xml.bind.JAXBContext
import org.glassfish.jaxb.runtime.v2.runtime.JAXBContextImpl
import java.nio.file.Path
import javax.xml.transform.stream.StreamSource
import kotlin.math.cos

class KmlRouteReader(
    private val file: Path,
    private val placemarkIndex: Int,
) : RouteReader {
    override fun read(): Route {
        val unmarshaller = jaxBContext.createUnmarshaller()

        val kmlRoot = unmarshaller.unmarshal(StreamSource(file.toFile()), Kml::class.java).value
        val placemark = when (val feature = kmlRoot.feature) {
            is Placemark -> feature
            is Document -> feature.feature
                .filterIsInstance<Placemark>()
                .drop(placemarkIndex)
                .firstOrNull()
                ?: throw IllegalArgumentException("Found fewer than ${placemarkIndex + 1} placemarks in $file")
            else -> throw IllegalArgumentException("Expected a Placemark or Document, got ${feature::class.simpleName}")
        }

        val lineString = placemark.geometry
        if (lineString !is LineString) {
            throw IllegalArgumentException("The geometry of placemark #$placemarkIndex in $file must be a LineString, got ${lineString::class.simpleName}")
        }

        return lineString.coordinates
            .asSequence()
            .windowed(size = 2, step = 1)
            .map { (a, b) -> a.euclideanVectorTo(b) }
            .map(::RoadSegment)
            .oversample(OPTIMAL_ROAD_SEGMENT_LENGTH)
            .toList()
    }

    private companion object {
        val jaxBContext: JAXBContext by lazy {
            JAXBContextImpl.JAXBContextBuilder()
                .setClasses(arrayOf(Kml::class.java))
                .build()
        }
    }
}

fun Coordinate.euclideanVectorTo(other: Coordinate): Vector3 {
    // we are processing KML files, which are based on Google Earth
    // Google Earth+Maps assume the earth is a perfect sphere
    // alas, we make the same assumption here for correctness of the result
    val earthRadius = 6378134.981

    val polarAngularDistance = other.latitude - this.latitude
    var equatorialAngularDistance = other.longitude - this.longitude
    if (equatorialAngularDistance < -180.0) {
        equatorialAngularDistance += 360.0
    } else if (equatorialAngularDistance > 180.0) {
        equatorialAngularDistance -= 360.0
    }
    val midPolarPosition = this.latitude + polarAngularDistance / 2.0

    val polarDistance = Math.toRadians(polarAngularDistance) * earthRadius * cos(Math.toRadians(midPolarPosition))
    val equatorialDistance = Math.toRadians(equatorialAngularDistance) * earthRadius

    return Vector3(
        polarDistance,
        equatorialDistance,
        other.altitude - this.altitude,
    )
}