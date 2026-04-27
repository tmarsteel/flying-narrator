package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReader
import io.github.tmarsteel.flyingnarrator.nefs.NefsCoordinates
import io.github.tmarsteel.flyingnarrator.nefs.NefsFile
import kotlinx.serialization.Serializable
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
data class RouteInfo(
    val modelId: Int,
    val countryName: String,
    val packName: String,
    val routeName: String,
    val languageKey: String,
    val startPosition: DR2TrackProgressPosition,
) {
    companion object {
        fun load(gameDirectory: Path): List<RouteInfo> {
            val nefsCoords = NefsCoordinates.Headless(
                gameDirectory.resolve("dirtrally2.exe"),
                gameDirectory.resolve("game"),
                Path.of("game_1.dat"),
            )
            val baseCtpkData = NefsFile.open(nefsCoords).use { game1dat ->
                game1dat.readFileByPath(listOf("catalogues", "base.ctpk"))
                    ?: throw IOException("Could not find base.ctpk in $nefsCoords")
            }

            val basePack = CTPKFile.parse(baseCtpkData)
            val trackModels = basePack.getTypedSection(CtpkTypes.BASE.TRACK_MODELS)
                .asSequence()
                .map {
                    RichTrackModel(
                        it.id,
                        basePack.strings.getValue(it.countryName),
                        basePack.strings.getValue(it.packNameId),
                        basePack.strings.getValue(it.routeNameId),
                        basePack.strings.getValue(it.disciplineNameId),
                        basePack.strings.getValue(it.lngKey),
                    )
                }
                .filter { it.disciplineName == "rally" }
                .filter { it.countryName !in setOf("skipfe", "material_testbed") }
                .filter { it.packName !in setOf("twin_peaks") }

            return trackModels
                .groupBy {
                    Path.of(it.countryName + "__"  + it.packName + ".nefs")
                }
                .entries
                .flatMap { (nefsFile, models) ->
                    amendWithPackInfo(gameDirectory.resolve("locations").resolve(nefsFile), models)
                }
        }

        private fun amendWithPackInfo(nefsFile: Path, models: List<RichTrackModel>): List<RouteInfo> {
            val countryName = models.map { it.countryName }.distinct().single()
            val packName = models.map { it.packName }.distinct().single()
            NefsFile.open(NefsCoordinates.FileOnSystemDisk(nefsFile)).use { packFile ->
                val routeDirs = packFile.listDirectoryByPath(listOf("tracks", "locations", countryName, packName)).associateBy { it.fileName }
                return models.map { model ->
                    val reader = DirtRally2RouteReader.fromNefs(packFile, routeDirs[model.routeName]!!.id)
                    RouteInfo(
                        model.id,
                        countryName,
                        model.packName,
                        model.routeName,
                        model.languageKey,
                        reader.startPosition,
                    )
                }
            }
        }

        data class RichTrackModel(
            val id: Int,
            val countryName: String,
            val packName: String,
            val routeName: String,
            val disciplineName: String,
            val languageKey: String,
        )
    }
}

fun main(args: Array<String>) {
    val gameDir = Paths.get(args[0])
    val routeInfos = RouteInfo.load(gameDir).sortedBy { it.modelId }

    for (route in routeInfos) {
        // 472uL to Vector3(1185.39, 606.282, -2669.52),
        val start = route.startPosition
        println(route.modelId.toUInt().toString() + "uL to Vector3(${start.x},${start.y},${start.z}),")
    }
}