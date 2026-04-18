package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.exc.MismatchedInputException
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement

class DR2ProgressGate(
    @XmlAttribute
    val id: Long,

    @XmlAttribute
    val distance: Double,

    @XmlElement
    val left: Position,
) {
    @JsonDeserialize(using = Position.Deserializer::class)
    class Position(
        val x: Double,
        val y: Double,
        val z: Double,
    ) {
        class Deserializer : ValueDeserializer<Position>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Position? {
                if (p.nextToken() != JsonToken.PROPERTY_NAME) {
                    throw MismatchedInputException.from(p, Position::class.java, "Expected attribute name")
                }
                val propName = p.currentName()
                if (propName != "format") {
                    throw MismatchedInputException.from(p, Position::class.java, "Expected attribute 'format'")
                }
                check(p.nextToken() == JsonToken.VALUE_STRING)
                val format = p.string
                if (p.nextToken() != JsonToken.PROPERTY_NAME || p.currentName() != "" || p.nextToken() != JsonToken.VALUE_STRING) {
                    throw MismatchedInputException.from(p, Position::class.java, "Expected tag content")
                }
                val content = p.string
                if (p.nextToken() != JsonToken.END_OBJECT) {
                    throw MismatchedInputException.from(p, Position::class.java, "Expected closing tag")
                }

                return decode(format, content) { msg ->
                    throw MismatchedInputException.from(p, Position::class.java, msg)
                }
            }

            fun decode(format: String, content: String, onError: (String) -> Nothing = { throw IllegalArgumentException(it) }): Position = when (format) {
                "float3" -> {
                    val coords = content.trim().split(' ')
                    if (coords.size != 3) {
                        onError("Expected 3 coordinates, got ${coords.size}")
                    }
                    Position(coords[0].toDouble(), coords[1].toDouble(), coords[2].toDouble())
                }
                else -> onError("Unknown gate position format: $format")
            }
        }
    }
}