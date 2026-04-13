package io.github.tmarsteel.flyingnarrator.dirtrally2

import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import javax.xml.bind.annotation.XmlElement

class DR2TrackSplineSet(
    @XmlElement
    @JsonProperty("spline")
    @JacksonXmlElementWrapper(useWrapping = false)
    val splines: List<DR2TrackSpline>
)