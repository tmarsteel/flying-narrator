package io.github.tmarsteel.flyingnarrator

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.io.path.readText
import kotlin.io.path.writer
import kotlin.math.roundToInt

fun main(args: Array<String>) {
    val route = Json.decodeFromString(ListSerializer(Vector3.serializer()),  Paths.get("Doukas - Lalas.json").readText())
        .trackSegments()

    ImageIO.write(route.map { it.roadSegment }.toList().render(scale = 2.0), "png", File("stage.png"))

    Paths.get("stage.csv").writer().use { writer ->
        writer.appendLine("length,angle,radius")
        route
            .map { v ->
                Row(v.roadSegment.length(), v.angleToNext, v.radiusToNext)
            }
            .forEach {
                writer.appendLine("${it.length2d},${Math.toDegrees(it.angleToNext)},${it.radiusToNext}")
            }
    }

    route.derivePacenotes().forEach { (d, i)  ->
        println("${d.roundToInt()}m: $i")
    }
}

data class Row(
    val length2d: Double,
    val angleToNext: Double,
    val radiusToNext: Double,
)