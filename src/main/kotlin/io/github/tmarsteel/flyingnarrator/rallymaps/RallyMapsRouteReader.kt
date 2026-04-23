package io.github.tmarsteel.flyingnarrator.rallymaps

import de.micromata.opengis.kml.v_2_2_0.Coordinate
import io.github.tmarsteel.flyingnarrator.euclideanVectorTo
import io.github.tmarsteel.flyingnarrator.feature.OPTIMAL_ROAD_SEGMENT_LENGTH
import io.github.tmarsteel.flyingnarrator.http.CachingUrlReader
import io.github.tmarsteel.flyingnarrator.route.RoadSegment
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.route.RouteReader
import io.github.tmarsteel.flyingnarrator.route.oversample
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.nio.file.Paths
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists

class RallyMapsRouteSource(
    val url: URL,
    urlReader: (URL) -> String = DEFAULT_URL_READER,
) {
    private val detailUrls: Map<Long, URL>
    val raceStageReaders: List<RaceReader>
    val rally: RallyDto

    init {
        val pageContent = urlReader(url)
        val json = RallyMapsSpider.extractRallyDataAsJSON(pageContent, url)
        try {
            rally = JSON_FORMAT.decodeFromString<RallyDto>(json)
        } catch (ex: SerializationException) {
            throw UnreadableRallyMapsPageException("Could not parse rally JSON", ex)
        }

        detailUrls = RallyMapsSpider.extractStageDetailURLs(pageContent, url)
        raceStageReaders = rally.stages
            .filter { it.stageType in setOf(StageDto.Type.RACE, StageDto.Type.SHAKEDOWN) }
            .map { RaceReader(it, detailUrls[it.id]) }
    }

    class RaceReader(
        val stage: StageDto,
        val detailsURL: URL?,
        urlReader: (URL) -> String = DEFAULT_URL_READER,
    ) : RouteReader {
        private val elevationProfile: List<Coordinate> by lazy {
            if (detailsURL == null) {
                throw UnreadableRallyMapsPageException("Could not find stage details URL for stage ${stage.id} / ${stage.name}")
            }

            RallyMapsSpider.extractElevationProfile(urlReader(detailsURL))
        }

        override fun read(): Route {
            val lineString = stage.geometries
                .map { it.geometry }
                .filterIsInstance<LineStringDto>()
                .firstOrNull() ?: throw UnreadableRallyMapsPageException("Could not find LineString geometry for stage ${stage.id} / ${stage.name}")

            return lineString.coordinates
                .asSequence()
                .windowed(size = 2, step = 1)
                .map { (a, b) ->
                    val a3 = Coordinate(a.longitude, a.latitude)
                    val b3 = Coordinate(b.longitude, b.latitude)
                    a3.euclideanVectorTo(b3)
                }
                .map(::RoadSegment)
                .oversample(OPTIMAL_ROAD_SEGMENT_LENGTH)
                .toList()
        }
    }

    companion object {
        val OKHTTP_CLIENT = OkHttpClient()
        val SIMPLE_URL_READER: (URL) -> String = {
            val response = OKHTTP_CLIENT.newCall(
                Request.Builder().url(it).build(),
            ).execute()
            if (response.code != 200) {
                throw UnreadableRallyMapsPageException("Got status code ${response.code}")
            }
            val body = response.body ?: throw UnreadableRallyMapsPageException("No body in response")
            body.string()
        }
        val DEFAULT_URL_READER = run {
            val cacheDir = System.getenv("FLYINGNARRATOR_CACHE_DIR")
                ?.let(Paths::get)
                ?: return@run SIMPLE_URL_READER
            if (cacheDir.notExists()) {
                cacheDir.createDirectory()
            }
            CachingUrlReader(cacheDir, SIMPLE_URL_READER)
        }

        val JSON_FORMAT = Json {
            ignoreUnknownKeys = true
        }
    }
}