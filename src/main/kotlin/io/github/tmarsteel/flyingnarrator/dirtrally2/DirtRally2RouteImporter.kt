package io.github.tmarsteel.flyingnarrator.dirtrally2

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.walk

class DirtRally2RouteImporter(
    val gameDirectory: Path,
) {
    val importableRoutes: Sequence<ImportableRoute> = gameDirectory.resolve("locations").walk()
        .mapNotNull { file ->
            val match = LOCATION_FILE_REGEX.matchEntire(file.name)
                ?: return@mapNotNull null

            file to match
        }
        .map { (nefsFile, match) ->
            ImportableRoute(
                locationName = match.groups["country"]!!.value,
                locationNumber = match.groups["number"]!!.value.toInt(),
                routeNumber = 0,
                name = nefsFile.nameWithoutExtension,
            )
        }

    data class ImportableRoute(
        val locationName: String,
        val locationNumber: Int,
        val routeNumber: Int,
        val name: String
    )

    companion object {
        private val LOCATION_FILE_REGEX = Regex("(?<country>\\w+)__(?<location>\\w+)_rally_(?<number>\\d+)\\.nefs")

        @JvmStatic
        fun main(args: Array<String>) {
            val gameDirectory = Paths.get(args[0])
            val importer = DirtRally2RouteImporter(gameDirectory)
            val routes = importer.importableRoutes.toList().map { it.name }
            println(routes)
        }
    }
}