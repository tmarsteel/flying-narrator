package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.easportswrc.EASportsWRCCleanGhostRouteReader
import io.github.tmarsteel.flyingnarrator.editor.JScrollPaneWithDragScroll
import io.github.tmarsteel.flyingnarrator.editor.RouteComponent
import java.awt.BorderLayout
import java.awt.Color
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JWindow
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