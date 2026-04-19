package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Node

interface SSMLElement {
    fun toDOMNode(document: Document): Node
}