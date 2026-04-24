package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.degrees
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.doubles.plusOrMinus
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

    "angleTowardsPositiveY" {
        up.clockwiseAngleFromPositiveY() shouldBe 0.degrees
        upRight.clockwiseAngleFromPositiveY() shouldBe 45.degrees
        right.clockwiseAngleFromPositiveY() shouldBe 90.degrees
        downRight.clockwiseAngleFromPositiveY() shouldBe 135.degrees
        down.clockwiseAngleFromPositiveY() shouldBe 180.degrees
        downLeft.clockwiseAngleFromPositiveY() shouldBe -135.degrees
        left.clockwiseAngleFromPositiveY() shouldBe -90.degrees
        upLeft.clockwiseAngleFromPositiveY() shouldBe -45.degrees

        Vector3(2.0, 1.0, 0.0).clockwiseAngleFromPositiveY().toDoubleInDegrees() shouldBe 63.43.plusOrMinus(0.01)
        Vector3(2.0, -1.0, 0.0).clockwiseAngleFromPositiveY().toDoubleInDegrees() shouldBe 116.57.plusOrMinus(0.01)
        Vector3(-2.0, 1.0, 0.0).clockwiseAngleFromPositiveY().toDoubleInDegrees() shouldBe (-63.43).plusOrMinus(0.01)
        Vector3(-2.0, -1.0, 0.0).clockwiseAngleFromPositiveY().toDoubleInDegrees() shouldBe (-116.57).plusOrMinus(0.01)
    }

    "starting up" - {
        "going up" {
            up.angleTo(up) shouldBe 0.0.degrees
        }

        "going up-right" {
            up.angleTo(upRight) shouldBe 45.0.degrees
        }

        "going right" {
            up.angleTo(right) shouldBe 90.0.degrees
        }

        "going down-right" {
            up.angleTo(downRight) shouldBe 135.0.degrees
        }

        "going down" {
            up.angleTo(down) shouldBe 180.0.degrees
        }

        "going down-left" {
            up.angleTo(downLeft) shouldBe -135.0.degrees
        }

        "going left" {
            up.angleTo(left) shouldBe -90.degrees
        }

        "going up-left" {
            up.angleTo(upLeft) shouldBe -45.degrees
        }
    }

    "starting up-right" - {
        "going up" {
            upRight.angleTo(up) shouldBe -45.0.degrees
        }

        "going up-right" {
            upRight.angleTo(upRight) shouldBe 0.degrees
        }

        "going right" {
            upRight.angleTo(right) shouldBe 45.0.degrees
        }

        "going down-right" {
            upRight.angleTo(downRight) shouldBe 90.0.degrees
        }

        "going down" {
            upRight.angleTo(down) shouldBe 135.0.degrees
        }

        "going down-left" {
            upRight.angleTo(downLeft) shouldBe -180.0.degrees
        }

        "going left" {
            upRight.angleTo(left) shouldBe -135.0.degrees
        }

        "going up-left" {
            upRight.angleTo(upLeft) shouldBe -90.0.degrees
        }
    }

    "starting right" - {
        "going up" {
            right.angleTo(up) shouldBe -90.0.degrees
        }

        "going up-right" {
            right.angleTo(upRight) shouldBe -45.0.degrees
        }

        "going right" {
            right.angleTo(right) shouldBe 0.0.degrees
        }

        "going down-right" {
            right.angleTo(downRight) shouldBe 45.0.degrees
        }

        "going down" {
            right.angleTo(down) shouldBe 90.0.degrees
        }

        "going down-left" {
            right.angleTo(downLeft) shouldBe 135.0.degrees
        }

        "going left" {
            right.angleTo(left) shouldBe -180.0.degrees
        }

        "going up-left" {
            right.angleTo(upLeft) shouldBe -135.0.degrees
        }
    }

    "starting down-right" - {
        "going up" {
            downRight.angleTo(up) shouldBe -135.0.degrees
        }

        "going up-right" {
            downRight.angleTo(upRight) shouldBe -90.0.degrees
        }

        "going right" {
            downRight.angleTo(right) shouldBe -45.degrees
        }

        "going down-right" {
            downRight.angleTo(downRight) shouldBe 0.0.degrees
        }

        "going down" {
            downRight.angleTo(down) shouldBe 45.0.degrees
        }

        "going down-left" {
            downRight.angleTo(downLeft) shouldBe 90.0.degrees
        }

        "going left" {
            downRight.angleTo(left) shouldBe 135.0.degrees
        }

        "going up-left" {
            downRight.angleTo(upLeft) shouldBe -180.0.degrees
        }
    }

    "starting down" - {
        "going up" {
            down.angleTo(up) shouldBe -180.0.degrees
        }

        "going up-right" {
            down.angleTo(upRight) shouldBe -135.0.degrees
        }

        "going right" {
            down.angleTo(right) shouldBe -90.degrees
        }

        "going down-right" {
            down.angleTo(downRight) shouldBe -45.degrees
        }

        "going down" {
            down.angleTo(down) shouldBe 0.0.degrees
        }

        "going down-left" {
            down.angleTo(downLeft) shouldBe 45.0.degrees
        }

        "going left" {
            down.angleTo(left) shouldBe 90.0.degrees
        }

        "going up-left" {
            down.angleTo(upLeft) shouldBe 135.0.degrees
        }
    }

    "starting down-left" - {
        "going up" {
            downLeft.angleTo(up) shouldBe 135.0.degrees
        }

        "going up-right" {
            downLeft.angleTo(upRight) shouldBe 180.0.degrees
        }

        "going right" {
            downLeft.angleTo(right) shouldBe -135.0.degrees
        }

        "going down-right" {
            downLeft.angleTo(downRight) shouldBe -90.0.degrees
        }

        "going down" {
            downLeft.angleTo(down) shouldBe -45.0.degrees
        }

        "going down-left" {
            downLeft.angleTo(downLeft) shouldBe 0.0.degrees
        }

        "going left" {
            downLeft.angleTo(left) shouldBe 45.0.degrees
        }

        "going up-left" {
            downLeft.angleTo(upLeft) shouldBe 90.0.degrees
        }
    }

    "starting left" - {
        "going up" {
            left.angleTo(up) shouldBe 90.0.degrees
        }

        "going up-right" {
            left.angleTo(upRight) shouldBe 135.0.degrees
        }

        "going right" {
            left.angleTo(right) shouldBe 180.0.degrees
        }

        "going down-right" {
            left.angleTo(downRight) shouldBe -135.0.degrees
        }

        "going down" {
            left.angleTo(down) shouldBe -90.0.degrees
        }

        "going down-left" {
            left.angleTo(downLeft) shouldBe -45.0.degrees
        }

        "going left" {
            left.angleTo(left) shouldBe 0.0.degrees
        }

        "going up-left" {
            left.angleTo(upLeft) shouldBe 45.0.degrees
        }
    }

    "starting up-left" - {
        "going up" {
            upLeft.angleTo(up) shouldBe 45.0.degrees
        }

        "going up-right" {
            upLeft.angleTo(upRight) shouldBe 90.0.degrees
        }

        "going right" {
            upLeft.angleTo(right) shouldBe 135.0.degrees
        }

        "going down-right" {
            upLeft.angleTo(downRight) shouldBe 180.0.degrees
        }

        "going down" {
            upLeft.angleTo(down) shouldBe -135.0.degrees
        }

        "going down-left" {
            upLeft.angleTo(downLeft) shouldBe -90.0.degrees
        }

        "going left" {
            upLeft.angleTo(left) shouldBe -45.0.degrees
        }

        "going up-left" {
            upLeft.angleTo(upLeft) shouldBe 0.0.degrees
        }
    }
})