package io.github.tmarsteel.flyingnarrator.dirtrally2

import com.fasterxml.jackson.annotation.JsonProperty
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
)

