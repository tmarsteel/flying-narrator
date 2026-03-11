package io.github.tmarsteel.flyingnarrator.rallymaps

import io.github.tmarsteel.flyingnarrator.Geospatial
import io.github.tmarsteel.flyingnarrator.Vector3
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.mozilla.javascript.Context
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.ast.AstNode
import org.mozilla.javascript.ast.AstRoot
import org.mozilla.javascript.ast.Comment
import org.mozilla.javascript.ast.ExpressionStatement
import org.mozilla.javascript.ast.FunctionCall
import org.mozilla.javascript.ast.ObjectLiteral
import org.mozilla.javascript.ast.ObjectProperty
import org.mozilla.javascript.ast.StringLiteral
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.stream.Collectors
import org.mozilla.javascript.Parser as JsParser

object RallyMapsSpider {
    private val jsContext = Context.enter()
    private val jsScope = jsContext.initStandardObjects()

    private fun unsupportedCode() = UnreadableRallyMapsPageException("Unexpected JavaScript or HTML source for stage data, cannot parse")

    private fun parseJS(code: String, url: URL, lineno: Int = 1): AstRoot {
        try {
            return JsParser().parse(code, url.toString(), lineno)
        } catch (ex: RhinoException) {
            throw UnreadableRallyMapsPageException("Could not parse JavaScript source for stage data", ex)
        }
    }

    fun extractRallyDataAsJSON(rallyPageSource: String, url: URL): String {
        val decryptionKey = deriveDecryptionKey(url)
        val dataCodeStartIndex = rallyPageSource.indexOf("sl.leaflet.data.storage.addData({")
        if (dataCodeStartIndex < 0) {
            throw UnreadableRallyMapsPageException("Could not find data code in page source. Please specify a URL to https://www.rally-maps.com/")
        }
        val dataCodeEndIndex = rallyPageSource.indexOf("});", dataCodeStartIndex);
        val dataCode = rallyPageSource.substring(dataCodeStartIndex, dataCodeEndIndex + 3)

        val parsed = parseJS(dataCode, url, 1)
        val addDataCall = (parsed.firstChild as? ExpressionStatement)?.expression as? FunctionCall ?: throw unsupportedCode()
        if (addDataCall.target.toSource() != "sl.leaflet.data.storage.addData" || addDataCall.arguments.size != 1) {
            throw unsupportedCode()
        }

        addDataCall.arguments[0].visit { node ->
            if (node is Comment) {
                node.parent.removeChild(node)
                return@visit true
            }

            if (node is ObjectLiteral) {
                val type = node.elements
                    .filterIsInstance<ObjectProperty>()
                    .find { it.key.toSource() == "type" }
                    ?.value

                val geometriesElement = node.elements
                    .filterIsInstance<ObjectProperty>()
                    .find { it.key.toSource() == "geometries" }

                if (type is StringLiteral && type.value == "stage" && geometriesElement != null && geometriesElement.value is StringLiteral) {
                    val rawJson = decrypt((geometriesElement.value as StringLiteral).value, decryptionKey)
                    val geometriesAsJsNode = parseJS(rawJson, url, geometriesElement.value.lineno)
                        .let { it.firstChild as? ExpressionStatement ?: throw unsupportedCode() }
                        .expression
                    geometriesElement.setKeyAndValue(geometriesElement.key, geometriesAsJsNode)
                    return@visit false
                }
            }

            true
        }

        val jsonString = jsContext.evaluateString(jsScope, "JSON.stringify(${addDataCall.arguments[0].toSource()})", url.toString(), 1, null)
        jsonString as? String ?: throw unsupportedCode()
        return jsonString
    }

    /**
     * @return key: [StageDto.id], value: the URL with the details, to be used with [extractElevationProfile].
     */
    fun extractStageDetailURLs(rallyPageSource: String): Map<Long, URL> {
        return Jsoup.parse(rallyPageSource)
            .selectFirst("table.rallyItinerary")
            .let { it ?: throw unsupportedCode() }
            .selectStream("tr[data-stage-id]")
            .map { stageDetailsTr ->
                val stageIdString = stageDetailsTr.attr("data-stage-id")
                val stageId = try {
                    stageIdString.toLong()
                } catch (ex: NumberFormatException) {
                    throw UnreadableRallyMapsPageException("Could not parse stage ID from HTML: $stageIdString", ex)
                }
                val urlString = stageDetailsTr.selectFirst("td.srname a[href]")
                    .let { it ?: throw unsupportedCode() }
                    .attr("href")
                val url = try {
                    URI.create(urlString).toURL()
                } catch (ex: IllegalArgumentException) {
                    throw UnreadableRallyMapsPageException("Could not parse URL for stage details $stageId: $urlString", ex)
                }

                stageId to url
            }
            .collect(Collectors.toMap(
                Pair<Long, URL>::first,
                Pair<Long, URL>::second,
                { url1, url2 ->
                    if (url1 != url2) {
                        throw UnreadableRallyMapsPageException("Multiple URLs for stage: $url1, $url2")
                    }
                    url1
                },
            ))
    }

    private val elevationDataRegex = Regex(",elevationData\\s*:\\s*(\\[\\{.+?)(?:\r|\n)")
    private val ELEVATION_JSON_FORMAT = Json {
        ignoreUnknownKeys = true
    }
    fun extractElevationProfile(stagePageSource: String): List<Geospatial> {
        val chartSetupCode = Jsoup.parse(stagePageSource)
            .selectFirst("section.box:has([id='Elevation Chart']) script")
            .let { it ?: throw unsupportedCode() }
            .data()

        val elevationData = elevationDataRegex.find(chartSetupCode)
            .let { it ?: throw unsupportedCode() }
            .groupValues[1]

        return try {
            ELEVATION_JSON_FORMAT.decodeFromString(ElevationDataSerializer, elevationData)
        } catch (ex: SerializationException) {
            throw UnreadableRallyMapsPageException("Could not parse elevation data", ex)
        }
    }

    /**
     * deobfuscated from function `l` in https://www.rally-maps.com/rm2/js/dist/bundle/scripts-leafletmap-bundle.min.js?v=2.337
     */
    private fun deriveDecryptionKey(url: URL): String {
        return url.toString().split('/')[0].replace(
            Regex("p:", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)),
            "ps:",
        ) + "//" + url.host.lowercase(Locale.ENGLISH)
    }

    /**
     * deobfuscated from function `de` in https://www.rally-maps.com/rm2/js/dist/bundle/scripts-leafletmap-bundle.min.js?v=2.337
     */
    private fun decrypt(encryptedText: String, key: String): String {
        var decryptedText = ""
        var charset = "                                "
        charset += " !\"#\$%&'()*+,-./0123456789:;<=>?"
        charset += "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_"
        charset += "`abcdefghijklmnopqrstuvwxyz{|}~"


        // Handle empty key or text
        if (key.isEmpty()) return encryptedText
        if (encryptedText.isEmpty()) return ""

        // Decryption loop
        var keyIndex = 0
        val textLength = encryptedText.length
        val keyLength = key.length

        var characterIndex = 0
        while (characterIndex < textLength) {
            var currentChar = encryptedText[characterIndex]
            val tempnum = charset.indexOf(currentChar)

            if (33 <= tempnum && tempnum <= 126) {
                val keyChar = key[keyIndex]
                val keyCharIndex = charset.lastIndexOf(keyChar)
                val decryptedCharIndex = if (tempnum >= keyCharIndex)
                    tempnum - keyCharIndex + 33
                else
                    tempnum + 94 - keyCharIndex + 33
                currentChar = charset[decryptedCharIndex]

                // Cycle through key
                keyIndex++
                if (keyIndex >= keyLength) {
                    keyIndex = 0
                }
            }

            decryptedText += currentChar
            characterIndex++
        }

        return decryptedText
    }
}