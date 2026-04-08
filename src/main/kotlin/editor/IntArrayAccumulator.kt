package io.github.tmarsteel.flyingnarrator.editor

class IntArrayAccumulator(
    initialCapacity: Int = 10,
) {
    var rawArray = IntArray(initialCapacity)
        private set

    var size = 0
        private set

    fun add(value: Int) {
        if (size == rawArray.size) {
            rawArray = rawArray.copyOf(rawArray.size * 2)
        }
        rawArray[size] = value
        size++
    }
}