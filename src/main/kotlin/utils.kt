package io.github.tmarsteel.flyingnarrator

import kotlin.math.sqrt

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

inline fun <T> Sequence<T>.weightedAverageOf(
    weight: (T) -> Double,
    value: (T) -> Double,
): Double {
    val iterator = this.iterator()
    if (!iterator.hasNext()) {
        throw NoSuchElementException()
    }

    var element = iterator.next()
    var currentAverage = value(element)
    var currentWeight = weight(element)
    while (iterator.hasNext()) {
        element = iterator.next()
        val nextValue = value(element)
        val nextWeight = weight(element)
        val nextCurrentWeight = currentWeight + nextWeight
        currentAverage = (currentAverage * currentWeight + nextValue * nextWeight) / nextCurrentWeight
        currentWeight = nextCurrentWeight
    }

    return currentAverage
}

fun Sequence<Double>.averageAndStandardDeviationOf(): Pair<Double, Double> {
    val average = average()
    val variance = map { (it - average) * (it - average) }.average()
    val stdDev = sqrt(variance)
    return Pair(average, stdDev)
}

fun <T> Sequence<T>.firstAndLast(): Pair<T, T> {
    val iterator = this.iterator()
    if (!iterator.hasNext()) {
        throw NoSuchElementException()
    }

    val first = iterator.next()
    var last = first
    while (iterator.hasNext()) {
        last = iterator.next()
    }
    return Pair(first, last)
}

fun <T> List<T>.mergeConsecutiveIf(
    shouldMerge: (T, T) -> Boolean,
    merge: (T, T) -> T,
): Sequence<T> {
    return sequence {
        val iterator = this@mergeConsecutiveIf.iterator()
        if (!iterator.hasNext()) {
            return@sequence
        }

        var pivot = iterator.next()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (shouldMerge(pivot, next)) {
                pivot = merge(pivot, next)
            } else {
                yield(pivot)
                pivot = next
            }
        }
        yield(pivot)
    }
}

/**
 * @return a view of `this` (see [List.subList]) as if applying both [dropWhile] and [dropLastWhile]
 * using [predicate].
 */
fun <T> List<T>.dropFirstAndLastWhile(predicate: (T) -> Boolean): List<T> {
    val firstIndex = this.indexOfFirst { !predicate(it) }
    val lastIndex = this.indexOfLast { !predicate(it) }
    return this.subList(firstIndex, lastIndex + 1)
}

fun <A, T> Sequence<T>.foldInto(mutableAcc: A, fold: (A, T) -> Unit): A {
    forEach { fold(mutableAcc, it) }
    return mutableAcc
}

fun <T> List<T>.windowsWhere(
    overlapping: Boolean = true,
    yieldCopies: Boolean = false,
    predicate: (windowCandidate: Iterable<T>) -> Boolean,
) : Sequence<List<T>> {
    return sequence {
        val queue = ArrayDeque<T>()
        val iterator = this@windowsWhere.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            queue.addLast(next)
            if (predicate(queue)) {
                yield(if (yieldCopies) queue.toList() else queue)
                if (overlapping) {
                    queue.removeFirst()
                } else {
                    queue.clear()
                }
            }
        }
    }
}