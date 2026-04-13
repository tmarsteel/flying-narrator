package io.github.tmarsteel.flyingnarrator

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class HermiteSplineTest : FreeSpec({
    val cp1 = HermiteSpline.ControlPoint(
        position = Vector3(-2640.370117, 1316.660034, 614.299988),
        tangent = Vector3(0.021594, -0.998237, -0.055281),
    )
    val cp2 = HermiteSpline.ControlPoint(
        position = Vector3(-2639.959961, 1297.699951,  613.25),
        tangent = Vector3(-0.021061, -0.998956, -0.040528),
    )

    "interpolate at extremes is identical to position" {
        HermiteSpline.interpolate(cp1, cp2, 0.0) shouldBe cp1.position
        HermiteSpline.interpolate(cp1, cp2, 1.0) shouldBe cp2.position
    }
})