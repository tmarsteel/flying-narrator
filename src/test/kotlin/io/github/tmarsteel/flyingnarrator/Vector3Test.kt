package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
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
        Math.toDegrees(up.clockwiseAngleFromPositiveY()) shouldBe 0.0
        Math.toDegrees(upRight.clockwiseAngleFromPositiveY()) shouldBe 45.0
        Math.toDegrees(right.clockwiseAngleFromPositiveY()) shouldBe 90.0
        Math.toDegrees(downRight.clockwiseAngleFromPositiveY()) shouldBe 135.0
        Math.toDegrees(down.clockwiseAngleFromPositiveY()) shouldBe 180.0
        Math.toDegrees(downLeft.clockwiseAngleFromPositiveY()) shouldBe -135.0
        Math.toDegrees(left.clockwiseAngleFromPositiveY()) shouldBe -90.0
        Math.toDegrees(upLeft.clockwiseAngleFromPositiveY()) shouldBe -45.0

        Math.toDegrees(Vector3(2.0, 1.0, 0.0).clockwiseAngleFromPositiveY()) shouldBe 63.43.plusOrMinus(0.01)
        Math.toDegrees(Vector3(2.0, -1.0, 0.0).clockwiseAngleFromPositiveY()) shouldBe 116.57.plusOrMinus(0.01)
        Math.toDegrees(Vector3(-2.0, 1.0, 0.0).clockwiseAngleFromPositiveY()) shouldBe (-63.43).plusOrMinus(0.01)
        Math.toDegrees(Vector3(-2.0, -1.0, 0.0).clockwiseAngleFromPositiveY()) shouldBe (-116.57).plusOrMinus(0.01)
    }

    "starting up" - {
        "going up" {
            Math.toDegrees(up.angleTo(up)) shouldBe 0.0
        }

        "going up-right" {
            Math.toDegrees(up.angleTo(upRight)) shouldBe 45.0
        }

        "going right" {
            Math.toDegrees(up.angleTo(right)) shouldBe 90.0
        }

        "going down-right" {
            Math.toDegrees(up.angleTo(downRight)) shouldBe 135.0
        }

        "going down" {
            Math.toDegrees(up.angleTo(down)) shouldBe 180.0
        }

        "going down-left" {
            Math.toDegrees(up.angleTo(downLeft)) shouldBe -135.0
        }

        "going left" {
            Math.toDegrees(up.angleTo(left)) shouldBe -90
        }

        "going up-left" {
            Math.toDegrees(up.angleTo(upLeft)) shouldBe -45
        }
    }

    "starting up-right" - {
        "going up" {
            Math.toDegrees(upRight.angleTo(up)) shouldBe -45.0
        }

        "going up-right" {
            Math.toDegrees(upRight.angleTo(upRight)) shouldBe 0
        }

        "going right" {
            Math.toDegrees(upRight.angleTo(right)) shouldBe 45.0
        }

        "going down-right" {
            Math.toDegrees(upRight.angleTo(downRight)) shouldBe 90.0
        }

        "going down" {
            Math.toDegrees(upRight.angleTo(down)) shouldBe 135.0
        }

        "going down-left" {
            Math.toDegrees(upRight.angleTo(downLeft)) shouldBe -180.0
        }

        "going left" {
            Math.toDegrees(upRight.angleTo(left)) shouldBe -135.0
        }

        "going up-left" {
            Math.toDegrees(upRight.angleTo(upLeft)) shouldBe -90.0
        }
    }

    "starting right" - {
        "going up" {
            Math.toDegrees(right.angleTo(up)) shouldBe -90.0
        }

        "going up-right" {
            Math.toDegrees(right.angleTo(upRight)) shouldBe -45.0
        }

        "going right" {
            Math.toDegrees(right.angleTo(right)) shouldBe 0.0
        }

        "going down-right" {
            Math.toDegrees(right.angleTo(downRight)) shouldBe 45.0
        }

        "going down" {
            Math.toDegrees(right.angleTo(down)) shouldBe 90.0
        }

        "going down-left" {
            Math.toDegrees(right.angleTo(downLeft)) shouldBe 135.0
        }

        "going left" {
            Math.toDegrees(right.angleTo(left)) shouldBe -180.0
        }

        "going up-left" {
            Math.toDegrees(right.angleTo(upLeft)) shouldBe -135.0
        }
    }

    "starting down-right" - {
        "going up" {
            Math.toDegrees(downRight.angleTo(up)) shouldBe -135.0
        }

        "going up-right" {
            Math.toDegrees(downRight.angleTo(upRight)) shouldBe -90.0
        }

        "going right" {
            Math.toDegrees(downRight.angleTo(right)) shouldBe -45
        }

        "going down-right" {
            Math.toDegrees(downRight.angleTo(downRight)) shouldBe 0.0
        }

        "going down" {
            Math.toDegrees(downRight.angleTo(down)) shouldBe 45.0
        }

        "going down-left" {
            Math.toDegrees(downRight.angleTo(downLeft)) shouldBe 90.0
        }

        "going left" {
            Math.toDegrees(downRight.angleTo(left)) shouldBe 135.0
        }

        "going up-left" {
            Math.toDegrees(downRight.angleTo(upLeft)) shouldBe -180.0
        }
    }

    "starting down" - {
        "going up" {
            Math.toDegrees(down.angleTo(up)) shouldBe -180.0
        }

        "going up-right" {
            Math.toDegrees(down.angleTo(upRight)) shouldBe -135.0
        }

        "going right" {
            Math.toDegrees(down.angleTo(right)) shouldBe -90
        }

        "going down-right" {
            Math.toDegrees(down.angleTo(downRight)) shouldBe -45
        }

        "going down" {
            Math.toDegrees(down.angleTo(down)) shouldBe 0.0
        }

        "going down-left" {
            Math.toDegrees(down.angleTo(downLeft)) shouldBe 45.0
        }

        "going left" {
            Math.toDegrees(down.angleTo(left)) shouldBe 90.0
        }

        "going up-left" {
            Math.toDegrees(down.angleTo(upLeft)) shouldBe 135.0
        }
    }

    "starting down-left" - {
        "going up" {
            Math.toDegrees(downLeft.angleTo(up)) shouldBe 135.0
        }

        "going up-right" {
            Math.toDegrees(downLeft.angleTo(upRight)) shouldBe 180.0
        }

        "going right" {
            Math.toDegrees(downLeft.angleTo(right)) shouldBe -135.0
        }

        "going down-right" {
            Math.toDegrees(downLeft.angleTo(downRight)) shouldBe -90.0
        }

        "going down" {
            Math.toDegrees(downLeft.angleTo(down)) shouldBe -45.0
        }

        "going down-left" {
            Math.toDegrees(downLeft.angleTo(downLeft)) shouldBe 0.0
        }

        "going left" {
            Math.toDegrees(downLeft.angleTo(left)) shouldBe 45.0
        }

        "going up-left" {
            Math.toDegrees(downLeft.angleTo(upLeft)) shouldBe 90.0
        }
    }

    "starting left" - {
        "going up" {
            Math.toDegrees(left.angleTo(up)) shouldBe 90.0
        }

        "going up-right" {
            Math.toDegrees(left.angleTo(upRight)) shouldBe 135.0
        }

        "going right" {
            Math.toDegrees(left.angleTo(right)) shouldBe 180.0
        }

        "going down-right" {
            Math.toDegrees(left.angleTo(downRight)) shouldBe -135.0
        }

        "going down" {
            Math.toDegrees(left.angleTo(down)) shouldBe -90.0
        }

        "going down-left" {
            Math.toDegrees(left.angleTo(downLeft)) shouldBe -45.0
        }

        "going left" {
            Math.toDegrees(left.angleTo(left)) shouldBe 0.0
        }

        "going up-left" {
            Math.toDegrees(left.angleTo(upLeft)) shouldBe 45.0
        }
    }

    "starting up-left" - {
        "going up" {
            Math.toDegrees(upLeft.angleTo(up)) shouldBe 45.0
        }

        "going up-right" {
            Math.toDegrees(upLeft.angleTo(upRight)) shouldBe 90.0
        }

        "going right" {
            Math.toDegrees(upLeft.angleTo(right)) shouldBe 135.0
        }

        "going down-right" {
            Math.toDegrees(upLeft.angleTo(downRight)) shouldBe 180.0
        }

        "going down" {
            Math.toDegrees(upLeft.angleTo(down)) shouldBe -135.0
        }

        "going down-left" {
            Math.toDegrees(upLeft.angleTo(downLeft)) shouldBe -90.0
        }

        "going left" {
            Math.toDegrees(upLeft.angleTo(left)) shouldBe -45.0
        }

        "going up-left" {
            Math.toDegrees(upLeft.angleTo(upLeft)) shouldBe 0.0
        }
    }
})