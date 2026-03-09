package io.github.tmarsteel.flyingnarrator

import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val route = KmlRouteReader(Paths.get(args[0]), args[1].toInt()).read()
    ImageIO.write(route.render(), "png", File("stage.png"))
}