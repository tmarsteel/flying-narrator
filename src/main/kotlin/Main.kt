package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.rallymaps.RallyMapsRouteSource
import java.io.File
import java.net.URI
import java.nio.file.Paths
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val source = RallyMapsRouteSource(URI.create("https://www.rally-maps.com/12-Uren-van-Aalst-1985/").toURL())
    val route = source
        .raceStageReaders
        .single { it.stage.name == "Comfisca" }
        .read()

    //val route = KmlRouteReader(Paths.get(args[0]), args[1].toInt()).read()
    ImageIO.write(route.render(), "png", File("stage.png"))
}