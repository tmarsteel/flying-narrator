package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverData
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverDataCall
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverDataSubcall
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLBreak
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLElement
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLEmphasis
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLSentence
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLText
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import io.github.tmarsteel.flyingnarrator.utils.join
import java.util.Locale
import kotlin.time.Duration

/**
 * Adapts data from [io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverData] to
 * [io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAtom]
 */
class DirtRally2PacenoteAtomAdapter(
    val previousCall: DR2CodriverDataCall?,
    val call: DR2CodriverDataCall,
) : PacenoteAtom {
    override val metadata = PacenoteAtom.Metadata(
        call.distanceAlongTrack.meters,
        if (call.subcalls.any { it.type == DR2CodriverDataSubcall.Type.FINISH }) 0.meters else -(1.meters),
    )

    override fun selectLocale(localePreference: List<Locale.LanguageRange>): Locale {
        return Locale.ENGLISH
    }

    override fun toSSML(locale: Locale): SSMLElement {
        if (locale != Locale.ENGLISH) throw IllegalArgumentException("unsupported locale $locale")

        val precedingSubcall = previousCall?.subcalls?.lastOrNull { !it.isNoop }
        val subcallsWithPrevious = (listOf(precedingSubcall) + call.subcalls).filterNot { it?.isNoop == true }
        val elements = subcallsWithPrevious
            .asSequence()
            .windowed(size = 2, partialWindows = false)
            .map { (previous, subcall) ->
                subcall!!.toWords(previous).toList()
            }
            .filter { it.isNotEmpty()  }
            .join(
                transform = { it.asSequence() },
                separator = listOf(SSMLText("; ")),
            )
            .toList()

        return SSMLSentence(
            children = elements + SSMLText("."),
        )
    }

    private fun DR2CodriverDataSubcall.toWords(previousSubcall: DR2CodriverDataSubcall?): Sequence<SSMLElement> {
        val words = mutableListOf<SSMLElement>()

        when (previousSubcall?.distanceLink) {
            DR2CodriverDataSubcall.DistanceLink.INTO -> {
                words.add(SSMLText("into "))
            }
            DR2CodriverDataSubcall.DistanceLink.PLUS -> {
                words.add(SSMLText("and "))
            }
            else -> {
                // nothing to do
            }
        }

        if (DR2CodriverDataSubcall.Modifier.CAUTION in listOf(modifierA, modifierB)) {
            words.add(SSMLText("caution "))
        }

        when (type) {
            DR2CodriverDataSubcall.Type.EMPTY,
            DR2CodriverDataSubcall.Type.STRAIGHT -> {
                // nothing to do, the work is done by the modifiers and the distanceLink
            }
            DR2CodriverDataSubcall.Type.LEFT_TURN,
            DR2CodriverDataSubcall.Type.RIGHT_TURN -> {
                words.add(SSMLText(DR2CodriverDataSubcall.Severity.ofAngle(angle).name.lowercase()))
                words.add(SSMLText(" " + if (type == DR2CodriverDataSubcall.Type.LEFT_TURN) {
                    "left"
                } else {
                    "right"
                }))
            }
            DR2CodriverDataSubcall.Type.BUMP_OR_CREST_OR_JUMP -> {
                words.add(SSMLText(" crest "))
            }
            DR2CodriverDataSubcall.Type.DIP -> {
                words.add(SSMLText(" dip "))
            }
            DR2CodriverDataSubcall.Type.FINISH -> {
                words.add(SSMLText(" over finish "))
            }
            else -> error("unsupported subcall $this")
        }

        if (DR2CodriverDataSubcall.Modifier.DONT_CUT in listOf(modifierA, modifierB)) {
            words.add(SSMLEmphasis(level = SSMLEmphasis.Level.STRONG, children = listOf(SSMLText("don't cut"))))
        }

        when (distanceLink) {
            DR2CodriverDataSubcall.DistanceLink.OPENS -> {
                words.add(SSMLText(" opens"))
            }
            DR2CodriverDataSubcall.DistanceLink.TIGHTENS -> {
                words.add(SSMLText(" tightens"))
            }
            DR2CodriverDataSubcall.DistanceLink.INTO,
            DR2CodriverDataSubcall.DistanceLink.PLUS -> {
                // is picked up by the next one
            }
            DR2CodriverDataSubcall.DistanceLink.NONE -> {
                // nothing to do :)
            }
            else -> {
                check(distanceLink.distanceInMeters != null) {
                    "unsupported distanceLink $distanceLink for subcall $this"
                }
                if (words.isNotEmpty()) {
                    // two calls in one
                    words.add(SSMLText(";"))
                }

                words.add(SSMLText(distanceLink.distanceInMeters.toString(10)))
            }
        }

        if (words.isNotEmpty() && delay > Duration.ZERO) {
            words.addFirst(SSMLBreak(time = delay))
        }

        return words.asSequence()
    }

    companion object {
        fun adapt(codriverData: DR2CodriverData): List<DirtRally2PacenoteAtomAdapter> {
            if (codriverData.codriverCalls.isEmpty()) return emptyList()
            val first = DirtRally2PacenoteAtomAdapter(null, codriverData.codriverCalls.first())
            val rest = codriverData.codriverCalls
                .windowed(2, 1, partialWindows = false)
                .map { (prev, next) ->
                    DirtRally2PacenoteAtomAdapter(prev, next)
                }

            return listOf(first) + rest
        }

        private val DR2CodriverDataSubcall.isNoop: Boolean
            get() = type == DR2CodriverDataSubcall.Type.EMPTY && distanceLink == DR2CodriverDataSubcall.DistanceLink.NONE
    }
}