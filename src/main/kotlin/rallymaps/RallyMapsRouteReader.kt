package io.github.tmarsteel.flyingnarrator.rallymaps

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import kotlin.io.path.readText

class RallyMapsRouteSource(
    val url: URL,
    urlReader: (URL) -> String = DEFAULT_URL_READER,
) {
    init {
        val pageContent = urlReader(url)
        val json = RallyMapsSpider.extractRallyDataAsJSON(pageContent, url)
    }

    companion object {
        val OKHTTP_CLIENT = OkHttpClient()
        val DEFAULT_URL_READER: (URL) -> String = {
            val response = OKHTTP_CLIENT.newCall(
                Request.Builder().url(it).build(),
            ).execute()
            if (response.code != 200) {
                throw UnreadableRallyMapsPageException("Got status code ${response.code}")
            }
            val body = response.body ?: throw UnreadableRallyMapsPageException("No body in response")
            body.string()
        }
    }
}

fun main() {
    val url = URI.create("https://www.rally-maps.com/Rally-in-the-100-Acre-Wood-2026").toURL()
    //val pageContent = RallyMapsRouteSource.DEFAULT_URL_READER(url)
    val pageContent = Paths.get("Rally-in-the-100-Acre-Wood-2026").readText()
    val json = RallyMapsSpider.extractRallyDataAsJSON(pageContent, url)
    val jsonParser = Json {
        ignoreUnknownKeys = true
    }
    val obj = jsonParser.decodeFromString<RallyDto>(json)
    obj.stages.forEach {
        println(it.name)
        println(it.stageType)
    }
    println(obj)
}