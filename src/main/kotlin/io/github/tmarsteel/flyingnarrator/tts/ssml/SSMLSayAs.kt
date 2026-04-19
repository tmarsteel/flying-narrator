package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Element

class SSMLSayAs(
    val interpretAs: Interpretation,
    val format: String?,
    val detail: Int?,
    val children: List<SSMLElement>,
) : SSMLElement {
    override fun toDOMNode(document: Document): Element {
        val el = document.createElement("say-as")

        el.setAttribute("interpret-as", interpretAs.toSSMLText())
        format?.let { el.setAttribute("format", it) }
        detail?.let { el.setAttribute("detail", it.toString()) }

        children.forEach { el.appendChild(it.toDOMNode(document)) }

        return el
    }

    enum class Interpretation {
        CURRENCY,
        TELEPHONE,
        VERBATIM,
        DATE,
        CHARACTERS,
        CARDINAL,
        ORDINAL,
        FRACTION,
        EXPLETIVE,
        UNIT,
        TIME,
        ;

        fun toSSMLText() = name.lowercase()
    }
}