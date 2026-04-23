package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.feature.AngleAccumulator
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class AngleAccumulatorTest : FreeSpec({
    "simple no overflow" {
        val acc = AngleAccumulator(Vector3(0.0, 1.0, 0.0))
        acc.currentAngle shouldBe 0.0

        acc.add(Vector3(1.0, 1.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(45.0)

        acc.add(Vector3(1.0, 1.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(45.0)

        acc.add(Vector3(1.0, 0.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(90.0)
    }

    "overflow to positive" {
        val acc = AngleAccumulator(Vector3(0.0, 1.0, 0.0))
        acc.add(Vector3(1.0, 1.0, 0.0))
        acc.add(Vector3(1.0, 0.0, 0.0))
        acc.add(Vector3(1.0, -1.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(135.0)
        acc.add(Vector3(0.0, -1.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(180.0)

        acc.add(Vector3(-1.0, -1.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(225.0)

        acc.add(Vector3(-1.0, -1.0, 0.0))
        acc.add(Vector3(-1.0, -1.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(225.0)

        acc.add(Vector3(-1.0, 0.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(270.0)

        acc.add(Vector3(-1.0, 1.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(315.0)

        acc.add(Vector3(0.0, 1.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(360.0)

        acc.add(Vector3(1.0, 1.0, 0.0))
        acc.currentAngle shouldBe Math.toRadians(405.0)
    }
})