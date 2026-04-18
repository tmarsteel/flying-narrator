package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement

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