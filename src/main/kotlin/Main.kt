package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.easportswrc.EASportsWRCCleanGhostRouteReader
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.math.roundToInt

fun main(args: Array<String>) {
    val route = EASportsWRCCleanGhostRouteReader(Paths.get("./easports-wrc-tracks/10.cleanghost.json").readText())
        .read()

    val features = route.trackSegments().detectFeatures()

    features.derivePacenotes().forEach { (d, i) ->
        println("${d.roundToInt()}m: $i")
    }
}