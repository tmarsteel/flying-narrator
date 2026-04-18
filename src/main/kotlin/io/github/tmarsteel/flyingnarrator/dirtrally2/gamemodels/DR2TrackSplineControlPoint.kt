package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.tmarsteel.flyingnarrator.Vector3
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

    /*
     * The upX, upY and upZ should, theoretically, describe a normal vector onto the track giving information
     * about track inclination. As observed in `germany_01/route_0` and `usa_01/route_3`, this vector is always
     * (0, 1, 0) though (so always pointing straight up).
     */

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
    fun positionEquals(other: DR2TrackSplineControlPoint, tolerance: Double): Boolean {
        val deltaX = other.x - this.x
        val deltaY = other.y - this.y
        val deltaZ = other.z - this.z
        val deltaDistance = Vector3(deltaX, deltaY, deltaZ).length
        return deltaDistance <= tolerance
    }

    override fun toString(): String {
        return """
            <cp posX="$x" posY="$y" posZ="$z" forX="$forwardX" forY="$forwardY" forZ="$forwardZ" upX="$upwardsX" upY="$upwardsY" upZ="$upwardsZ" /> 
        """.trimIndent()
    }
}