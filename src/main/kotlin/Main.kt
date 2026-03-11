package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.rallymaps.RallyMapsRouteSource
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    val route = Json.decodeFromString(ListSerializer(Vector3.serializer()),  Paths.get("Comfisca.json").readText())

    //val route = KmlRouteReader(Paths.get(args[0]), args[1].toInt()).read()
    ImageIO.write(route.render(), "png", File("stage.png"))
}