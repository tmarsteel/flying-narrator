package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.time.Duration

class SSMLProsody(
    val pitch: Pitch?,
    val contour: List<PitchContourControlPoint>?,
    val duration: Duration?,
    val children: List<SSMLElement>,
) : SSMLElement {
    override fun toDOMNode(document: Document): Element {
        val el = document.createElement("prosody")

        pitch?.let { el.setAttribute("pitch", it.toSSMLText()) }
        duration?.let { el.setAttribute("duration", it.toSSMLText()) }
        contour?.let {
            el.setAttribute(
                "contour",
                it.joinToString(" ", transform = PitchContourControlPoint::toSSMLText),
            )
        }

        children.forEach { el.appendChild(it.toDOMNode(document)) }

        return el
    }

    sealed interface Pitch {
        fun toSSMLText(): String

        class Frequency(val hertz: Long) : Pitch {
            override fun toSSMLText() = "${hertz}Hz"
        }

        sealed class Interpreted(val label: String) : Pitch {
            override fun toSSMLText() = label
        }
        object ExtraLow : Interpreted("x-low")
        object Low : Interpreted("x-low")
        object Medium : Interpreted("x-low")
        object High : Interpreted("x-low")
        object ExtraHigh : Interpreted("x-low")
        object Default : Interpreted("x-low")

        sealed class Relative(val factor: Double) : Pitch {
            override fun toSSMLText(): String {
                val percentage = ((factor - 1.0) * 100.0).toInt().toString(10)
                val plusSign = if (factor > 1.0) "+" else ""
                return "$plusSign$percentage%"
            }
        }
    }

    sealed class PitchContourControlPoint(
        val timePercentage: Int,
        val pitch: Pitch,
    ) {
        init {
            require(timePercentage in 0..100)
        }
        fun toSSMLText() = "($timePercentage%,$pitch)"
    }
}