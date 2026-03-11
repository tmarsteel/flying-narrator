package io.github.tmarsteel.flyingnarrator.rallymaps

import kotlinx.serialization.SerializationException
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
        val rallyDto = try {
            JSON_FORMAT.decodeFromString<RallyDto>(json)
        } catch (ex: SerializationException) {
            throw UnreadableRallyMapsPageException("Could not parse rally JSON", ex)
        }


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

        val JSON_FORMAT = Json {
            ignoreUnknownKeys = true
        }
    }
}

fun main() {
    val url = URI.create("https://www.rally-maps.com/12-Uren-van-Aalst-1985/Comfisca#Elevation%20Chart").toURL()
    //val pageContent = RallyMapsRouteSource.DEFAULT_URL_READER(url)
    val pageContent = Paths.get("Comfisca").readText()
    val elevations = RallyMapsSpider.extractElevationProfile(pageContent)
    println(elevations.size)
    println(elevations)
}