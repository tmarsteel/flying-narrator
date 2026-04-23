package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Node

object SSMLNullElement : SSMLElement {
    override val isEmpty = true

    override fun toDOMNode(document: Document): Node {
        return document.createTextNode("")
    }
}