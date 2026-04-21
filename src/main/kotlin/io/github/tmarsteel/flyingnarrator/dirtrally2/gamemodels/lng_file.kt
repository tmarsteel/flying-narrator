package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReadingException
import io.github.tmarsteel.flyingnarrator.nefs.NefsCoordinates
import io.github.tmarsteel.flyingnarrator.nefs.NefsFile
import io.github.tmarsteel.flyingnarrator.nefs.protocol.Command
import tools.jackson.databind.util.ByteBufferBackedInputStream
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import java.nio.file.Path
import java.util.Locale
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "language")
class DR2LanguageFile(
    @param:JsonProperty("entry")
    @param:JacksonXmlElementWrapper(useWrapping = false)
    val entries: List<Entry>
) {
    data class Entry(
        @param:JsonProperty("LNG_Key")
        @param:XmlElement
        val key: String,

        @param:JsonProperty("LNG_Value")
        @param:XmlElement
        val value: String,
    )

    companion object {
        private val LANGUAGE_FILE_REGEX = Regex("language_(?<name>\\w{3}).lng")
        private val LANGUAGE_NAME_TO_LOCALE = mapOf(
            "eng" to Locale.of("en", "UK"),
            "use" to Locale.of("en", "US"),
            "ger" to Locale.of("de", "DE"),
            "fre" to Locale.of("fr", "FR"),
            "ita" to Locale.of("it", "IT"),
            "spa" to Locale.of("es", "ES"),
            "pol" to Locale.of("pl", "PL"),
            "bra" to Locale.of("pt", "BR"),
            "jpn" to Locale.of("ja", "JP"),
        )

        fun loadTranslations(gameDirectory: Path): Map<String, Map<Locale, String>> {
            val byLocale = NefsFile.open(NefsCoordinates.Headless(
                gameDirectory.resolve("dirtrally2.exe"),
                gameDirectory.resolve("game"),
                Path.of("game_1.dat"),
            )).use { game1dat ->
                val languageDir = game1dat.listFiles(recursive = false)
                    .asSequence()
                    .filter { it.isDirectory && it.fileName == "language" }
                    .singleOrNull()
                    ?: throw DirtRally2RouteReadingException("Did not find translations in game files")

                val byLocale = game1dat.listFiles(recursive = false, directory = languageDir.id)
                    .mapNotNull { languageFile ->
                        val locale = LANGUAGE_FILE_REGEX.matchEntire(languageFile.fileName)
                            ?.groupValues[1]
                            ?.let(LANGUAGE_NAME_TO_LOCALE::get)
                            ?: return@mapNotNull null

                        try {
                            val xmlData = game1dat.readFile(languageFile.id, Command.Conversion.LANGUAGE_FILE_TO_XML)
                            val parsedFile = DR2XMLMapper.readValue(
                                ByteBufferBackedInputStream(xmlData),
                                DR2LanguageFile::class.java
                            )
                            val map = parsedFile.entries.associate { it.key to it.value.trim() }
                            return@mapNotNull locale to map
                        }
                        catch (ex: Exception) {
                            System.err.println("Could not parse language file $languageFile")
                            ex.printStackTrace()
                            null
                        }
                    }
                    .toMap()

                return@use byLocale
            }

            val byKey = HashMap<String, HashMap<Locale, String>>()
            byLocale.forEach { (locale, translations) ->
                translations.forEach { (key, value) ->
                    byKey.getOrPut(key) { HashMap() }[locale] = value
                }
            }

            return byKey
        }
    }
}