package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Element

class SSMLMark(
    val name: String,
) : SSMLElement {
    override val isEmpty = true

    override fun toDOMNode(document: Document): Element {
        return document.createElement("mark").apply {
            setAttribute("name", name)
        }
    }
}