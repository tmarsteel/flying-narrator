package io.github.tmarsteel.flyingnarrator

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class Vector3Test : FreeSpec({
    val up = Vector3(0.0, 1.0, 0.0)
    val upRight = Vector3(1.0, 1.0, 0.0)
    val right = Vector3(1.0, 0.0, 0.0)
    val downRight = Vector3(1.0, -1.0, 0.0)
    val down = Vector3(0.0, -1.0, 0.0)
    val downLeft = Vector3(-1.0, -1.0, 0.0)
    val left = Vector3(-1.0, 0.0, 0.0)
    val upLeft = Vector3(-1.0, 1.0, 0.0)

    "up to up is 0°" {
        Math.toDegrees(up.angleFrom(up)) shouldBe 0.0
    }

    "up to up-right is 45°" {
        Math.toDegrees(upRight.angleFrom(up)) shouldBe 45.0
    }

    "up-right to up is -45°" {
        Math.toDegrees(up.angleFrom(upRight)) shouldBe -45.0
    }

    "down-right to down-left is 90°" {
        Math.toDegrees(downLeft.angleFrom(downRight)) shouldBe 90.0
    }

    "down-left to down-right is 90°" {
        Math.toDegrees(downRight.angleFrom(downLeft)) shouldBe -90.0
    }

    "up to down is 180°" {
        Math.toDegrees(down.angleFrom(up)) shouldBe 180.0
    }

    "down to up is 180°" {
        Math.toDegrees(down.angleFrom(up)) shouldBe 180.0
    }
})