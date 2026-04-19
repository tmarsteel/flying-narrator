package io.github.tmarsteel.flyingnarrator.tts.ssml

import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.time.Duration

fun Duration.toSSMLText(): String {
    val millis = inWholeMilliseconds
    return if (millis % 1000 == 0L) {
        "${inWholeSeconds}s"
    } else {
        "${millis}ms"
    }
}

fun ssmlToString(ssml: SSMLDocument, pretty: Boolean = false): String {
    val domDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
    domDocument.appendChild(ssml.toDOM(domDocument))
    val transformer = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        setOutputProperty(OutputKeys.VERSION, "1.0")
        setOutputProperty(OutputKeys.INDENT, if (pretty) "yes" else "no")
    }
    val writer = StringWriter()
    transformer.transform(DOMSource(domDocument), StreamResult(writer))
    return writer.toString()
}