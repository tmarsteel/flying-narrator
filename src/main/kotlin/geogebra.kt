package io.github.tmarsteel.flyingnarrator

import java.util.stream.IntStream

fun List<RoadSegment>.toGeogebraSyntax(): String {
    fun pointName(index: Int) = index
        // avoid using X, Y and Z because reserved in geogebra
        .toString(23)
        .chars()
        .map {
            when {
                it in 48..57 -> it + 17
                else -> it - 32 + 10
            }
        }
        .collectCodePointsToString()
    fun vecName(index: Int) = pointName(index).lowercase()

    val sb = StringBuilder()
    sb.appendLine(
        """
        ggbApplet.getAllObjectNames().forEach(o => ggbApplet.deleteObject(o));
        ggbApplet.evalCommand("${pointName(0)}=Point({0,0})");
        ggbApplet.setLayer("${pointName(0)}", 1);
    """.trimIndent()
    )
    this.forEachIndexed { index, segment ->
        val prevPointName = pointName(index)
        val pointName = pointName(index + 1)
        val vecName = vecName(index)
        sb.appendLine(
            """
            ggbApplet.evalCommand("$pointName=$prevPointName+Vector((${segment.forward.x},${segment.forward.y}))");
            ggbApplet.evalCommand("$vecName=Vector($prevPointName,$pointName)");
            ggbApplet.setLayer("$pointName", 1);
            ggbApplet.setLayer("$vecName", 0);
        """.trimIndent()
        )
    }

    fun lotLineName(index: Int) = "lot${pointName(index)}"
    for (index in 1 until this.count()) {
        val prevPointName = pointName(index - 1)
        val pointName = pointName(index)
        val nextPointName = pointName(index + 1)
        val lotLineName = lotLineName(index)
        sb.appendLine(
            """
            ggbApplet.evalCommand("$lotLineName=Line($pointName, AngleBisector($prevPointName, $pointName, $nextPointName))");
            ggbApplet.setLayer("$lotLineName", 1);
        """.trimIndent()
        )
    }

    fun centerPointName(index: Int) = "c${pointName(index)}"
    for (index in 1 until this.count() - 1) {
        val lotLineName = lotLineName(index)
        val nextLotLineName = lotLineName(index + 1)
        val centerPointName = centerPointName(index)
        sb.appendLine(
            """
            ggbApplet.evalCommand("$centerPointName=Intersect($lotLineName, $nextLotLineName)");
            ggbApplet.setLayer("$centerPointName", 1);
        """.trimIndent()
        )
    }

    fun arcName(index: Int) = "a${pointName(index)}"
    fun radiusName(index: Int) = "r${arcName(index)}"
    for ((index, segment) in this.withIndex().drop(1).dropLast(1)) {
        val centerPointName = centerPointName(index)
        val pointName = pointName(index)
        val nextPointName = pointName(index + 1)
        val arcName = arcName(index)
        val radiusName = radiusName(index)
        val angleToNext = segment.forward.angleTo(this[index + 1].forward)
        if (angleToNext < 0.0) {
            sb.appendLine(
                """
                ggbApplet.evalCommand("$arcName=CircularArc($centerPointName, $pointName, $nextPointName)");
            """.trimIndent()
            )
        } else {
            sb.appendLine(
                """
                ggbApplet.evalCommand("$arcName=CircularArc($centerPointName, $nextPointName, $pointName)");
            """.trimIndent()
            )
        }
        sb.appendLine(
            """
            ggbApplet.evalCommand("$radiusName=Radius($arcName)");
            ggbApplet.setLayer("$arcName", 2);
            ggbApplet.setLayer("$radiusName", 2);
        """.trimIndent()
        )
    }

    sb.appendLine(
        """
        ggbApplet.setLayerVisible(0, true);
        ggbApplet.setLayerVisible(1, false);
        ggbApplet.setLayerVisible(2, true);
    """.trimIndent()
    );

    return sb.toString()
}

fun ggbClear() {
    println("""ggbApplet.getAllObjectNames().forEach(o => ggbApplet.deleteObject(o));""")
}
fun ggbCmd(cmd: String) {
    println("ggbApplet.evalCommand(\"$cmd\");")
}
fun ggbPoint(name: String, position: Vector3) {
    ggbCmd("$name=Point({${position.x},${position.y}})")
}
fun ggbPoint3D(
    name: String,
    position: Vector3,
    color: Int? = null,
) {
    ggbCmd("$name=Point({${position.x},${position.y},${position.z}})")
    if (color != null) {
        ggbColor(name, color)
    }
}
fun ggbColor(name: String, color: Int) {
    println("""ggbApplet.setColor("$name", ${(color shr 16) and 0xFF}, ${(color shr 8) and 0xFF}, ${color and 0xFF});""")
}
private fun IntStream.collectCodePointsToString(): String {
    return collect(
        { StringBuilder() },
        { sb, cp -> sb.appendCodePoint(cp) },
        { sb1, sb2 -> sb1.append(sb2) },
    ).toString()
}