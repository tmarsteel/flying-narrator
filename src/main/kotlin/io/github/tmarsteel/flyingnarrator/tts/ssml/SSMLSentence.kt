package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Element

class SSMLSentence(
    val children: List<SSMLElement>,
) : SSMLElement {
    constructor(vararg children: SSMLElement) : this(children.toList())

    override fun toDOMNode(document: Document): Element {
        val el = document.createElement("s")
        children.forEach { el.appendChild(it.toDOMNode(document)) }

        return el
    }
}