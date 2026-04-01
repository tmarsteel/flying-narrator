package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.RepeatFirstAndLastSequence.Companion.repeatFirstAndLast
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class RepeatFirstAndLastSequenceTest : FreeSpec({
    "test" {
        val sequence = sequenceOf(1, 2, 3, 4, 5)
        sequence.repeatFirstAndLast().toList() shouldBe listOf(1, 1, 2, 3, 4, 5, 5)
    }

    "empty" {
        emptySequence<String>().repeatFirstAndLast().toList() shouldBe emptyList()
    }

    "single element" {
        sequenceOf(1).repeatFirstAndLast().toList() shouldBe listOf(1, 1)
    }

    "two elements" {
        sequenceOf(1, 2).repeatFirstAndLast().toList() shouldBe listOf(1, 1, 2, 2)
    }
})