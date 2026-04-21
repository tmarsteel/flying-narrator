package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.time.Duration

class SSMLBreak(
    val time: Duration? = null,
    val strength: Strength? = null,
) : SSMLElement {
    enum class Strength(val value: String) {
        NONE("none"),
        EXTRA_WEAK("x-weak"),
        WEAK("weak"),
        MEDIUM("medium"),
        STRONG("string"),
        EXTRA_STRONG("x-strong"),
    }

    override fun toDOMNode(document: Document): Element {
        val el = document.createElement("break")
        if (time != null) {
            el.setAttribute("time", time.toSSMLText())
        }
        if (strength != null) {
            el.setAttribute("strength", strength.value)
        }
        return el
    }
}