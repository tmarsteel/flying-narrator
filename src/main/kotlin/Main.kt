package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.easportswrc.EASportsWRCCleanGhostRouteReader
import java.awt.Color
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.io.path.readText
import kotlin.math.roundToInt

fun main(args: Array<String>) {
    val route = EASportsWRCCleanGhostRouteReader(Paths.get("./easports-wrc-tracks/10.cleanghost.json").readText())
        .read()
        .trackSegments()

    val features = route.detectFeatures()

    ImageIO.write(
        route.map { it.roadSegment }.toList().render(
            scale = 1.0,
            segmentJointMarkerColor = null,
            distanceMarkersEveryMeters = 200.0,
            features = features,
            trackColor = { f ->
                when (f) {
                    is Feature.Corner -> Color.ORANGE
                    else -> Color.BLACK
                }
            }
        ), "png", File("stage.png")
    )

    features.derivePacenotes().forEach { (d, i) ->
        println("${d.roundToInt()}m: $i")
    }
}