package io.github.tmarsteel.flyingnarrator.pacenote.inferred

import io.github.tmarsteel.flyingnarrator.utils.averageAndStandardDeviationOf
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

fun main() {
    // t, r, d, a, rs
    val data = Paths.get("corner-radii-observed-in-game.csv").readText()
        .splitToSequence('\n')
        .drop(1)
        .filter { it.isNotBlank() }
        .map { r -> r.split(';').map { l -> l.trimStart('"').trimEnd('"') } }
        .toList()

    val refSevs = listOf("1", "2", "3", "4", "5", "6", "slight")
    val nDistValues = 0.0.upTo(400.0, nValues = 75)
    Paths.get("vis.csv").writer().use { w ->
        w.write("v," + refSevs.joinToString(",") + "\n")
        val dists = refSevs
            .associateWith { refSev ->
                data
                    .filter { it[4] == refSev }
                    .map { (ts, rs, ds) ->
                        val t = ts.germanToDouble()
                        val d = ds.fromMeters()
                        val r = rs.fromMeters()
                        r
                    }
                    .let { nDist(nDistValues, it) }
            }

        nDistValues.forEach { v ->
            w.write("$v,")
            for (refSev in refSevs) {
                w.write(dists.getValue(refSev).getValue(v).toString())
                w.write(",")
            }
            w.write("\n")
        }
    }
}

private fun String.germanToDouble() = replace(',', '.').toDouble()
private fun String.fromMeters() = trimEnd('m').germanToDouble()

private fun nDist(nDistValues: Sequence<Double>, data: List<Double>): Map<Double, Double> {
    var (avg, stDev) = data.averageAndStandardDeviationOf()
    stDev /= 2.0
    return nDistValues
        .associateWith(normalDistribution(avg, stDev))
}

private fun normalDistribution(mean: Double, stDev: Double): (Double) -> Double {
    return { value ->
        val n = 1.0 / (stDev * sqrt(2 * PI)) * exp(-0.5 * ((value - mean) / stDev).pow(2))
        n
    }
}

private fun Double.upTo(other: Double, nValues: Int): Sequence<Double> {
    val step = (other - this) / (nValues.toDouble() - 1)
    return generateSequence(this) { it + step }.takeWhile { it < other } + sequenceOf(other)
}