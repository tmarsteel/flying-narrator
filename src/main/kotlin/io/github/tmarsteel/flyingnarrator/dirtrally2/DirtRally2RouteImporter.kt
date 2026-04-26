package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2LanguageFile
import io.github.tmarsteel.flyingnarrator.nefs.NefsCoordinates
import io.github.tmarsteel.flyingnarrator.nefs.NefsFile
import io.github.tmarsteel.flyingnarrator.nefs.NefsFileRef
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.name
import kotlin.io.path.walk

class DirtRally2RouteImporter(
    val gameDirectory: Path,
) {
    val translations = DR2LanguageFile.loadTranslations(gameDirectory)
    val importableRoutes: Sequence<ImportableRoute> = gameDirectory.resolve("locations").walk()
        .filter { LOCATION_FILE_REGEX.matchEntire(it.name) != null }
        .flatMap { routePackFilePath ->
            loadRoutePackFile(routePackFilePath, translations).asSequence()
        }

    data class ImportableRoute(
        val packFile: NefsCoordinates,
        val routeDirInPackFile: NefsFileRef,
        val locationName: Map<Locale, String>,
        val name: Map<Locale, String>,
    ) {
        fun read(): DirtRally2RouteReader {
            NefsFile.open(packFile).use { routePackFile ->
                return DirtRally2RouteReader.fromNefs(routePackFile, routeDirInPackFile.id)
            }
        }
    }

    companion object {
        private val LOCATION_FILE_REGEX = Regex("(?<country>\\w+)__(?<location>\\w+)_rally_(?<number>\\d+)\\.nefs")
        private val ROUTE_DIR_REGEX = Regex("/tracks/locations/(?<country>\\w+)/(?<locationTechnicalName>(?<location>\\w+)_rally_(?<locationNumber>\\d+))/route_(?<routeNumber>\\d+)")

        private fun loadRoutePackFile(
            routePackFilePath: Path,
            translations: Map<String, Map<Locale, String>>,
        ): List<ImportableRoute> {
            val routePackFileCoordinates = NefsCoordinates.FileOnSystemDisk(routePackFilePath)
            return NefsFile.open(routePackFileCoordinates).use { routePackFile ->
                routePackFile.listFiles(recursive = true)
                    .filter { it.isDirectory }
                    .mapNotNull {
                        val match = ROUTE_DIR_REGEX.matchEntire(it.fullPath)
                            ?: return@mapNotNull null
                        it to match
                    }
                    .map { (routeDir, data) ->
                        val location = data.groups["location"]!!.value
                        val routeNumber = data.groups["routeNumber"]!!.value.toInt()
                        val locationTechnicalName = data.groups["locationTechnicalName"]!!.value
                        val routeTechnicalName = locationTechnicalName + "_route_$routeNumber"
                        ImportableRoute(
                            routePackFileCoordinates,
                            routeDir,
                            translations["lng_location_full_${location}"]
                                ?: translations["lng_location_full_${location}_gravel"]
                                ?: emptyMap(),
                            translations["lng_$routeTechnicalName"] ?: emptyMap(),
                        )
                    }
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val gameDirectory = Paths.get(args[0])
            val importer = DirtRally2RouteImporter(gameDirectory)
            val routes = importer.importableRoutes.take(10).toList()
            println(routes)
        }
    }
}