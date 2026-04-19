package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Node

class SSMLText(
    val text: String,
) : SSMLElement {
    override fun toDOMNode(document: Document): Node {
        return document.createTextNode(text)
    }
}