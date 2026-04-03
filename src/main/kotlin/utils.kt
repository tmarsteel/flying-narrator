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