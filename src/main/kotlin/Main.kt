package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2SplineRouteReader
import java.nio.file.Paths

fun main(args: Array<String>) {
    val reader = DirtRally2SplineRouteReader(Paths.get(args[0]))

    ggbClear()
    reader.splineDto.leftSplineOriginal.controlPoints.drop(20).take(50).forEachIndexed { index, cp ->
        ggbPoint3D("l$index", cp.position, 0xFF0000)
    }
    reader.splineDto.centreLeftSplineOriginal.controlPoints.drop(20).take(50).forEachIndexed { index, cp ->
        ggbPoint3D("cl$index", cp.position, 0xFF8C00)
    }
    reader.splineDto.centralSplineOriginal.controlPoints.drop(20).take(50).forEachIndexed { index, cp ->
        ggbPoint3D("c$index", cp.position, 0x000000)
    }
    reader.splineDto.centreRightSplineOriginal.controlPoints.drop(20).take(50).forEachIndexed { index, cp ->
        ggbPoint3D("cr$index", cp.position, 0xFF8C00)
    }
    reader.splineDto.rightSplineOriginal.controlPoints.drop(20).take(50).forEachIndexed { index, cp ->
        ggbPoint3D("r$index", cp.position, 0xFF0000)
    }
}