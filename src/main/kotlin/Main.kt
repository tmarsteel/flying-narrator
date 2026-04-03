package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.easportswrc.EASportsWRCCleanGhostRouteReader
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.io.path.readText
import kotlin.math.roundToInt

fun main(args: Array<String>) {
    val route = EASportsWRCCleanGhostRouteReader(Paths.get("./easports-wrc-tracks/22.cleanghost.json").readText())
        .read()
        .trackSegments()

    ImageIO.write(
        route.map { it.roadSegment }.toList().render(
            scale = 2.0,
            segmentJointMarkerColor = null,
            distanceMarkersEveryMeters = 50.0,
        ), "png", File("stage.png")
    )

    route.derivePacenotes().forEach { (d, i)  ->
        println("${d.roundToInt()}m: $i")
    }
}