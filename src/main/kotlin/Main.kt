package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.easportswrc.EASportsWRCCleanGhostRouteReader
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import io.github.tmarsteel.flyingnarrator.pacenote.derivePacenotes
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

fun main(args: Array<String>) {
    val route = EASportsWRCCleanGhostRouteReader(Paths.get("./easports-wrc-tracks/10.cleanghost.json").readText())
        .read()

    val features = Feature.discoverIn(route)

    features
        .filterIsInstance<Feature.Corner>()
        .forEach { f ->
            val t = f.segments.maxOf { it.turnyness.absoluteValue }
            val r = f.segments.compoundRadius
            val d = f.segments.sumOf { it.arcLength }
            val a = Math.toDegrees(f.totalAngle.absoluteValue)
            println("$t;$r;$d;$a")
        }


    features.derivePacenotes().forEach { (d, i) ->
        println("${d.roundToInt()}m: $i")
    }
}