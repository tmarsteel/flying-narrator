package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Node

interface SSMLElement {
    /**
     * `true` iff this element has no pronuncable text
     */
    val isEmpty: Boolean

    fun toDOMNode(document: Document): Node
}