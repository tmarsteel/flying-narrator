package io.github.tmarsteel.flyingnarrator

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

/**
 * @return a view of `this` (see [List.subList]) as if applying both [dropWhile] and [dropLastWhile]
 * using [predicate].
 */
fun <T> List<T>.dropFirstAndLastWhile(predicate: (T) -> Boolean): List<T> {
    val firstIndex = this.indexOfFirst { !predicate(it) }
    val lastIndex = this.indexOfLast { !predicate(it) }
    return this.subList(firstIndex, lastIndex + 1)
}