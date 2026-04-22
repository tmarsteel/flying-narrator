package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2RouteReader
import io.github.tmarsteel.flyingnarrator.feature.AvgWindow
import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.TmpSegment
import java.nio.file.Paths
import kotlin.io.path.writer

fun main(args: Array<String>) {
    val reader = DirtRally2RouteReader(Paths.get(args[0]))
    val tmpSegments = TmpSegment.fromRoute(reader.read())
    val avgWindows = AvgWindow.fromTmpSegments(tmpSegments)

    Feature.discoverIn(reader.read())

    Paths.get("vis.csv").writer().use { writer ->
        writer.write("d,angle,delta\n")
        avgWindows.forEach {
            writer.write("${it.tmpSegments.first().startsAtTrackDistance},${it.angle},${it.deltaAnglePerArcMeter}\n")
        }
    }
}