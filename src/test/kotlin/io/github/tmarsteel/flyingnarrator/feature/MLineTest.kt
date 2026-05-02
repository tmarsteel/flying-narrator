package io.github.tmarsteel.flyingnarrator.feature

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class MLineTest : FreeSpec({
    "vertical" - {
        val a = Vector3(-15.34, 6.48, 0.0)
        val b = Vector3(7.06, 6.28, 0.0)
        val c = Vector3(-20.54,-4.12, 0.0)

        val lineAB = MLine(a, b - a)

        "with line" {
            val vertical = lineAB.findVerticalLineThrough(c)
            vertical.somePoint.x.shouldBeWithinPercentageOf(-20.444950179354322, 0.001)
            vertical.somePoint.y.shouldBeWithinPercentageOf(6.525579912315664, 0.001)
            vertical.somePoint.z.shouldBe(0.0)
            vertical.direction.x.shouldBeWithinPercentageOf(-0.09504982064567713, 0.001)
            vertical.direction.y.shouldBeWithinPercentageOf(-10.645579912315664, 0.001)
            vertical.direction.z.shouldBe(0.0)
        }

        "with line segment" - {
            "on line segment" {
                val d = Vector3(-12.54,-4.12, 0.0)
                val vertical = lineAB.findVerticalLineThrough(d, onlyIfOnSegment = true)
                vertical.shouldNotBeNull()
                vertical.somePoint.x.shouldBeWithinPercentageOf(-12.44558788361897, 0.001)
                vertical.somePoint.y.shouldBeWithinPercentageOf(6.45415703467517, 0.001)
                vertical.somePoint.z.shouldBe(0.0)
                vertical.direction.x.shouldBeWithinPercentageOf(-0.09441211638102942, 0.001)
                vertical.direction.y.shouldBeWithinPercentageOf(-10.57415703467517, 0.001)
                vertical.direction.z.shouldBe(0.0)
            }
            "not on line segment" {
                val vertical = lineAB.findVerticalLineThrough(c, onlyIfOnSegment = true)
                vertical shouldBe null
            }
        }
    }
})