package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.DropLastSequence.Companion.dropLast
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class DropLastSequenceTest : FreeSpec({
    "fewer elements" {
        sequenceOf(1, 2, 3, 4).dropLast(5).toList() shouldBe emptyList()
    }

    "as many elements" {
        sequenceOf(1, 2, 3, 4, 5).dropLast(5).toList() shouldBe emptyList()
    }

    "more elements" {
        sequenceOf(1, 2, 3, 4, 5, 6).dropLast(3).toList() shouldBe listOf(1, 2, 3)
    }
})