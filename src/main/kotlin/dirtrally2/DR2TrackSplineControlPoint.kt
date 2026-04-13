package io.github.tmarsteel.flyingnarrator.dirtrally2

import com.fasterxml.jackson.annotation.JsonProperty
import javax.xml.bind.annotation.XmlAttribute

data class DR2TrackSplineControlPoint(
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
) {
    override fun toString(): String {
        return """
            <cp posX="$x" posY="$y" posZ="$z" forX="$forwardX" forY="$forwardY" forZ="$forwardZ" upX="$upwardsX" upY="$upwardsY" upZ="$upwardsZ" /> 
        """.trimIndent()
    }
}