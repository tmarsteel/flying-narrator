package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverDataCall
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverDataSubcall
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLBreak
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLElement
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLSentence
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLText
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * Adapts data from [io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverData] to
 * [io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom]
 */
class DirtRally2PacenoteAtomAdapter(
    val call: DR2CodriverDataCall
) : PacenoteAtom {
    override val physicalFeaturesAtDistanceAlongRoute: Double
        get() = call.distanceAlongTrack.toDouble()


    override fun selectLocale(localePreference: List<Locale.LanguageRange>): Locale {
        return Locale.ENGLISH
    }

    override fun toSSML(locale: Locale): SSMLElement {
        if (locale != Locale.ENGLISH) throw IllegalArgumentException("unsupported locale $locale")

        return SSMLSentence(
            SSMLText(
                    call.subcalls.asSequence()
                        .flatMap { it.toWords() }
                        .joinToString(
                            separator = " ",
                            postfix = ".",
                        )
                ),
            SSMLBreak(time = 0.5.seconds), // to make sure sentences don't flow into each other.
        )
    }

    private fun DR2CodriverDataSubcall.toWords(): Sequence<String> {
        val words = mutableListOf<String>()

        if (DR2CodriverDataSubcall.Modifier.CAUTION in listOf(modifierA, modifierB)) {
            words.add("caution")
        }

        when (type) {
            DR2CodriverDataSubcall.Type.EMPTY,
            DR2CodriverDataSubcall.Type.STRAIGHT -> {
                // nothing to do, the work is done by the modifiers and the distanceLink
            }
            DR2CodriverDataSubcall.Type.LEFT_TURN,
            DR2CodriverDataSubcall.Type.RIGHT_TURN -> {
                words.add(DR2CodriverDataSubcall.Severity.ofAngle(angle).name.lowercase())
                if (type == DR2CodriverDataSubcall.Type.LEFT_TURN) {
                    words.add("left")
                } else {
                    words.add("right")
                }
            }
            DR2CodriverDataSubcall.Type.BUMP_OR_CREST_OR_JUMP -> {
                words.add("crest")
            }
            DR2CodriverDataSubcall.Type.DIP -> {
                words.add("dip")
            }
            DR2CodriverDataSubcall.Type.FINISH -> {
                words.add("over finish")
            }
            else -> error("unsupported subcall $this")
        }

        if (DR2CodriverDataSubcall.Modifier.DONT_CUT in listOf(modifierA, modifierB)) {
            words.add("don't cut")
        }

        when (distanceLink) {
            DR2CodriverDataSubcall.DistanceLink.OPENS -> {
                words.add("opens")
            }
            DR2CodriverDataSubcall.DistanceLink.TIGHTENS -> {
                words.add("tightens")
            }
            DR2CodriverDataSubcall.DistanceLink.INTO -> {
                words.add("into")
            }
            DR2CodriverDataSubcall.DistanceLink.PLUS -> {
                words.add("and")
            }
            DR2CodriverDataSubcall.DistanceLink.NONE -> {
                // nothing to do :)
            }
            else -> {
                check(distanceLink.distanceInMeters != null) {
                    "unsupported distanceLink $distanceLink for subcall $this"
                }
                words.add(distanceLink.distanceInMeters.toString(10))
            }
        }

        return words.asSequence()
    }
}