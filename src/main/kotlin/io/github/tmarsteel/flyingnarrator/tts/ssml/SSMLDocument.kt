package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.Locale

class SSMLDocument(
    val lang: Locale,
    val elements: List<SSMLElement>,
) {
    fun toDOM(document: Document): Element {
        return document.createElement("speak").apply {
            setAttribute("version", "1.1")
            setAttribute("xmlns", "http://www.w3.org/2001/10/synthesis")
            setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
            setAttribute(
                "xsi:schemaLocation",
                "http://www.w3.org/2001/10/synthesis http://www.w3.org/TR/speech-synthesis/synthesis.xsd"
            )
            setAttribute("xml:lang", lang.toLanguageTag())
            elements.forEach { appendChild(it.toDOMNode(document)) }
        }
    }
}