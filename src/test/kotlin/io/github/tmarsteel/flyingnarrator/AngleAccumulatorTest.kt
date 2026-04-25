package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.feature.AngleAccumulator
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.degrees
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class AngleAccumulatorTest : FreeSpec({
    "simple no overflow" {
        val acc = AngleAccumulator(Vector3(0.0, 1.0, 0.0))
        acc.currentAngle shouldBe 0.0.degrees

        acc.add(Vector3(1.0, 1.0, 0.0))
        acc.currentAngle shouldBe 45.0.degrees

        acc.add(Vector3(1.0, 1.0, 0.0))
        acc.currentAngle shouldBe 45.0.degrees

        acc.add(Vector3(1.0, 0.0, 0.0))
        acc.currentAngle shouldBe 90.0.degrees
    }

    "overflow to positive" {
        val acc = AngleAccumulator(Vector3(0.0, 1.0, 0.0))
        acc.add(Vector3(1.0, 1.0, 0.0))
        acc.add(Vector3(1.0, 0.0, 0.0))
        acc.add(Vector3(1.0, -1.0, 0.0))
        acc.currentAngle shouldBe 135.0.degrees
        acc.add(Vector3(0.0, -1.0, 0.0))
        acc.currentAngle shouldBe 180.0.degrees

        acc.add(Vector3(-1.0, -1.0, 0.0))
        acc.currentAngle shouldBe 225.0.degrees

        acc.add(Vector3(-1.0, -1.0, 0.0))
        acc.add(Vector3(-1.0, -1.0, 0.0))
        acc.currentAngle shouldBe 225.0.degrees

        acc.add(Vector3(-1.0, 0.0, 0.0))
        acc.currentAngle shouldBe 270.0.degrees

        acc.add(Vector3(-1.0, 1.0, 0.0))
        acc.currentAngle shouldBe 315.0.degrees

        acc.add(Vector3(0.0, 1.0, 0.0))
        acc.currentAngle shouldBe 360.0.degrees

        acc.add(Vector3(1.0, 1.0, 0.0))
        acc.currentAngle shouldBe 405.0.degrees
    }
})