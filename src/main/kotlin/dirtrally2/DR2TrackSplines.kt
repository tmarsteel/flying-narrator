package io.github.tmarsteel.flyingnarrator.dirtrally2

import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

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