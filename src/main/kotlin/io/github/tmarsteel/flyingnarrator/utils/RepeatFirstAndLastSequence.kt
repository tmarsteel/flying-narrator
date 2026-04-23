package io.github.tmarsteel.flyingnarrator.utils

/**
 * A sequence that emits the first element twice before continuing with the second element,
 * and emits the last element twice.
 */
class RepeatFirstAndLastSequence<T>(
    val base: Sequence<T>,
    val shouldRepeatFirst: Boolean = true,
    val shouldRepeatLast: Boolean = true,
) : Sequence<T> {
    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            private var firstSeen = false
            private var baseIterator = base.iterator()
            private var itemToRepeat: T? = null
            private var repeatItem = false

            override fun hasNext(): Boolean {
                return repeatItem || baseIterator.hasNext()
            }

            override fun next(): T {
                if (repeatItem) {
                    repeatItem = false
                    val localItem = itemToRepeat!!
                    itemToRepeat = null
                    return localItem
                }

                if (!firstSeen) {
                    val first = baseIterator.next()
                    firstSeen = true
                    if (shouldRepeatFirst) {
                        itemToRepeat = first
                        repeatItem = true
                    }
                    return first
                }

                val next = baseIterator.next()
                if (!baseIterator.hasNext()) {
                    if (shouldRepeatLast) {
                        itemToRepeat = next
                        repeatItem = true
                    }
                }

                return next
            }
        }
    }

    companion object {
        fun <T> Sequence<T>.repeatFirst() = RepeatFirstAndLastSequence(this, shouldRepeatLast = false)
        fun <T> Sequence<T>.repeatFirstAndLast() = RepeatFirstAndLastSequence(this)
        fun <T> Sequence<T>.repeatLast() = RepeatFirstAndLastSequence(this, shouldRepeatFirst = false)
    }
}