package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.github.tmarsteel.flyingnarrator.io.JacksonDurationAsMillisecondsDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement
import kotlin.time.Duration

/**
 * Data model for the co-driver callouts (symbols shown on-screen and references to the audio to play back)
 * found in the location `*.nefs` file in `$GAME_DIR/locations` at path
 * `tracks/locations/$location/$location_rally_$number/route_$stageNumber/$location_rally_$number_codriver_?_pro_?_data.xml`.
 */
@XmlRootElement(name = "root")
data class DR2CodriverData(
    @XmlElement
    @JsonProperty("shakedown_info")
    val shakedownInfo: DR2CodriverDataShakedownInfo,

    @XmlElement
    @JsonProperty("codriver_calls")
    val codriverCalls: List<DR2CodriverDataCall>
)

/**
 * Models a callout obtained from an audio file, plus the visual icons
 * the game displays alongside. Triggered when the car crosses [distanceAlongTrack].
 */
data class DR2CodriverDataCall(
    @XmlAttribute(required = false)
    val name: String,

    @XmlAttribute
    val audioName: String,

    @XmlAttribute
    val distanceAlongTrack: Int,

    @XmlElement
    @JsonProperty("subcall")
    @JacksonXmlElementWrapper(useWrapping = false)
    val subcalls: List<DR2CodriverDataSubcall>,
)

data class DR2CodriverDataShakedownInfo(
    @XmlAttribute
    val cutoffDistance: Int,

    @XmlAttribute
    val finishCallDistance: Int,
)

/**
 * Data for one or two closely related icons that show up on screen
 */
data class DR2CodriverDataSubcall(
    @XmlAttribute
    val type: Type,

    /**
     * Delay after triggering (for the first subcall) or after the previous subcall
     * has been shown
     */
    @XmlAttribute
    @field:JsonDeserialize(using = JacksonDurationAsMillisecondsDeserializer::class)
    val delay: Duration,

    /**
     * Angle of the corner in degrees, see [Severity]
     */
    @XmlAttribute
    val angle: Int,

    /**
     * is displayed on the bottom right
     */
    @XmlAttribute
    val modifierA: Modifier,

    /**
     * is displayed on the top left
     */
    @XmlAttribute
    val modifierB: Modifier,

    /**
     * usage unknown // TODO
     */
    @XmlAttribute
    val canConcatModA: Boolean,

    /**
     * usage unknown // TODO
     */
    @XmlAttribute
    val canConcatModB: Boolean,

    @XmlAttribute
    val distanceLink: DistanceLink,
) {
    enum class Type(@JsonValue val numeric: Int) {
        /**
         * For some reason, calls _always_ have 3 subcalls; and type `0` is used when less than 3 are needed
         *
         * **HOWEVER**, there are subcalls with `type="0"` and `distanceLink` != `0`. In this case, the distanceLink
         * would still render (e.g. just an "opens" symbol)
         */
        EMPTY(0),

        LEFT_TURN(1),

        RIGHT_TURN(2),

        /**
         * A straight section indicated by just a number
         */
        STRAIGHT(3),

        /**
         * not yet observed
         */
        TYPE4(4),

        /**
         * not yet observed
         */
        TYPE5(5),

        FINISH(6),

        DIP(7),

        /**
         * not yet observed
         */
        TYPE8(8),

        BUMP_OR_CREST_OR_JUMP(9),
        ;
    }

    enum class Modifier(@JsonValue val number: Int) {
        NONE(0),
        CAUTION(1),
        // TODO: 2
        DONT_CUT(3),
        // TODO: any above 3?
        ;
    }

    /**
     * You would think that distances are denoted in meters; but no, every individual distance value in the game
     * has a seemingly arbitrary number.
     */
    enum class DistanceLink(
        @JsonValue val numeric: Int,
        val distanceInMeters: Int? = null,
    ) {
        NONE(0),
        INTO(1),
        /** Rendered as a +, vocalized "and" in enUS */
        PLUS(2),
        S060(3, 60),
        S080(4, 80),
        S100(5, 100),
        S150(6, 150),
        S200(7, 200),
        S250(8, 250),
        // TODO: 9-11
        TIGHTENS(12),
        OPENS(13),
        // TODO: 14
        S050(15, 50),
        S070(16, 70),
        // TODO: 17-18
        S120(19, 200),
        S130(20, 130),
        // TODO: 21
        S160(22, 160),
        // TODO: 23
        S180(24, 180),
        // TODO: 25-61
        S030(61, 30),
        // TODO: any above?
        ;
    }

    /**
     * The severities as rendered in game, depending on [DR2CodriverDataSubcall.angle]
     */
    enum class Severity(
        /**
         * The maximum value of [DR2CodriverDataSubcall.angle] for this severity to apply. However, it appears
         * that most of the severities are just a 1:1 mapping between some angle value and a severity.
         */
        val maxAngle: Int
    ) {
        /**
         * observed:
         * * 15
         */
        SIX(15),

        /**
         * observed:
         * * 30
         */
        FIVE(30),

        /**
         * observed:
         * * 40
         */
        FOUR(40),

        /**
         * observed:
         * * 50
         */
        THREE(50),

        /**
         * observed:
         * * 65
         */
        TWO(65),

        /**
         * observed:
         * * 80
         */
        ONE(80),

        /**
         * observed:
         * * 90
         */
        SQUARE(90),

        /**
         * observed:
         * * 140
         * * 150
         */
        HAIRPIN(360),

        ;

        companion object {
            fun ofAngle(angle: Int): Severity = enumValues<Severity>().firstOrNull { it.maxAngle >= angle }
                ?: throw IllegalArgumentException("Unsupported severity angle: $angle")
        }
    }
}