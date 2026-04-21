package io.github.tmarsteel.flyingnarrator.tts.ssml

import org.w3c.dom.Document
import org.w3c.dom.Element
import tools.jackson.databind.MapperFeature
import tools.jackson.dataformat.xml.XmlFactory
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.jaxb.JaxbAnnotationModule
import tools.jackson.module.kotlin.kotlinModule
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

    companion object {
        val SSML_MAPPER by lazy {
            XmlMapper.Builder(XmlFactory())
                .addModule(kotlinModule())
                .addModule(JaxbAnnotationModule())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .build()
        }
    }
}