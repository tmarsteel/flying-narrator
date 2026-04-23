package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

/**
 * Model for the `progress_track.xml` file - found in the `*.nefs` files in `$GAME_DIR/locations`, at sub-path
 * `tracks/locations/$location/$location_rally_$number/route_$stageNumber/progress_track.xml`.
 */
@XmlRootElement(name = "progress_track_data")
class DR2ProgressTrackData(
    @XmlAttribute
    @JsonProperty("exporter_version")
    val exporterVersion: String,

    @XmlElement
    @JacksonXmlElementWrapper(useWrapping = true)
    val routes: List<DR2ProgressRoute>,

    @XmlElement
    @JacksonXmlElementWrapper(useWrapping = true)
    val gates: List<DR2ProgressGate>,

    /*
    there is also a <track> element, it specifies a "total_distance" which is pretty close
    to the last gates "distance", and a cubic bounding-box for the track
     */
)

class DR2ProgressRouteSplit(
    @XmlAttribute
    val id: Long,

    @XmlAttribute
    val type: Type,

    @XmlAttribute(name = "gate")
    val gateId: Long,
) {
    enum class Type(@JsonValue val jsonValue: String) {
        START("start"),
        TIME("time"),
        FINISH("finish"),
        ;

        companion object {
            @JvmStatic
            @JsonCreator
            fun fromJsonValue(jsonValue: String): DR2ProgressRoute.Direction = enumValues<DR2ProgressRoute.Direction>()
                .firstOrNull { it.jsonValue == jsonValue }
                ?: throw IllegalArgumentException("Unknown direction: $jsonValue")
        }
    }
}

class DR2ProgressRoute(
    @XmlAttribute
    val id: Long,

    @XmlAttribute
    val direction: Direction,

    @XmlAttribute
    @JsonProperty("num_splits")
    val numSplits: Int,

    @XmlElement(name = "split")
    @JsonProperty("split")
    @JacksonXmlElementWrapper(useWrapping = false)
    val splits: List<DR2ProgressRouteSplit>
) {
    enum class Direction(@JsonValue val jsonValue: String) {
        FORWARDS("forwards"),
        REVERSE("reverse"), // todo: is this right??
        ;

        companion object {
            @JvmStatic
            @JsonCreator
            fun fromJsonValue(jsonValue: String): Direction = enumValues<Direction>()
                .firstOrNull { it.jsonValue == jsonValue }
                ?: throw IllegalArgumentException("Unknown direction: $jsonValue")
        }
    }
}

class DR2ProgressGate(
    @XmlAttribute
    val id: Long,

    @XmlAttribute
    val distance: Double,

    @XmlElement
    val left: DR2TrackProgressPosition,

    @XmlElement
    val right: DR2TrackProgressPosition,

    @XmlElement
    val crossing: DR2TrackProgressPosition,
)

@JsonDeserialize(using = DR2TrackProgressPosition.Deserializer::class)
class DR2TrackProgressPosition(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    class Deserializer : ValueDeserializer<DR2TrackProgressPosition>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): DR2TrackProgressPosition? {
            if (p.nextToken() != JsonToken.PROPERTY_NAME) {
                throw MismatchedInputException.from(p, DR2TrackProgressPosition::class.java, "Expected attribute name")
            }
            val propName = p.currentName()
            if (propName != "format") {
                throw MismatchedInputException.from(p, DR2TrackProgressPosition::class.java, "Expected attribute 'format'")
            }
            check(p.nextToken() == JsonToken.VALUE_STRING)
            val format = p.string
            if (p.nextToken() != JsonToken.PROPERTY_NAME || p.currentName() != "" || p.nextToken() != JsonToken.VALUE_STRING) {
                throw MismatchedInputException.from(p, DR2TrackProgressPosition::class.java, "Expected tag content")
            }
            val content = p.string
            val decoded = decode(format, content) { msg ->
                throw MismatchedInputException.from(p, DR2TrackProgressPosition::class.java, msg)
            }

            if (p.nextToken() != JsonToken.END_OBJECT) {
                throw MismatchedInputException.from(p, DR2TrackProgressPosition::class.java, "Expected closing tag")
            }

            return decoded
        }

        fun decode(format: String, content: String, onError: (String) -> Nothing = { throw IllegalArgumentException(it) }): DR2TrackProgressPosition = when (format) {
            "float3" -> {
                val coords = content.trim().split(' ')
                if (coords.size != 3) {
                    onError("Expected 3 coordinates for format float3, got ${coords.size}")
                }
                DR2TrackProgressPosition(coords[0].toDouble(), coords[1].toDouble(), coords[2].toDouble())
            }
            else -> onError("Unknown position format: $format")
        }
    }
}