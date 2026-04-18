package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import javax.xml.bind.annotation.XmlAttribute

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