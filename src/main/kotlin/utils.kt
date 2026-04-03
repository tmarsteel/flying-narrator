package io.github.tmarsteel.flyingnarrator

import java.util.LinkedList

inline fun <T> Sequence<T>.averageOf(
    selector: (T) -> Double,
): Double {
    val iterator = this.iterator()
    if (!iterator.hasNext()) {
        throw NoSuchElementException()
    }

    var average = selector(iterator.next())
    while (iterator.hasNext()) {
        average = (average + selector(iterator.next())) / 2.0
    }
    return average
}

class DropLastSequence<T>(
    private val base: Sequence<T>,
    private val count: Int,
) : Sequence<T> {
    init {
        require(count > 0) { "count must be positive" }
    }

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            private var initialized = false
            private val lookahead = ArrayDeque<T>(count)
            private val baseIterator = base.iterator()

            private fun assureInitialized() {
                if (initialized) {
                    return
                }
                initialized = true
                repeat(count) {
                    if (!baseIterator.hasNext()) {
                        lookahead.clear()
                        return
                    }

                    lookahead.addLast(baseIterator.next())
                }
            }

            override fun hasNext(): Boolean {
                assureInitialized()
                return lookahead.isNotEmpty() && baseIterator.hasNext()
            }

            override fun next(): T {
                if (!hasNext()) {
                    throw NoSuchElementException()
                }

                val next = lookahead.removeFirst()
                lookahead.addLast(baseIterator.next())
                if (!baseIterator.hasNext()) {
                    lookahead.clear()
                }

                return next
            }
        }
    }

    companion object {
        fun <T> Sequence<T>.dropLast(count: Int) = DropLastSequence(this, count)
    }
}