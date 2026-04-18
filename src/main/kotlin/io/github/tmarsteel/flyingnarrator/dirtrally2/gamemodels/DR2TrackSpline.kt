package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import javax.xml.bind.annotation.XmlElement

class DR2TrackSpline(
    @XmlElement
    @JsonProperty("cp")
    @JacksonXmlElementWrapper(useWrapping = false)
    val controlPoints: List<DR2TrackSplineControlPoint>,
)