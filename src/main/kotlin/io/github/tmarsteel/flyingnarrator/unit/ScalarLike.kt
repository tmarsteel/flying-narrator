package io.github.tmarsteel.flyingnarrator.unit

interface ScalarLike<Self> : Comparable<Self> {
    operator fun plus(other: Self): Self
    operator fun minus(other: Self): Self
    operator fun div(scalar: Double): Self
    operator fun div(scalar: Int): Self = div(scalar.toDouble())
    operator fun times(scalar: Double): Self
    operator fun times(scalar: Int): Self = times(scalar.toDouble())

    val sign: Double
    fun withSign(sign: Double): Self
    operator fun unaryMinus(): Self {
        return withSign(-sign)
    }
    val absoluteValue: Self get()= if (sign < 0) withSign(-sign) else withSign(sign)

    companion object {
        fun <S : ScalarLike<S>> Sequence<S>.sum(): S {
            return reduce { acc, a -> acc + a }
        }
        fun <S : ScalarLike<S>> Iterable<S>.sum(): S = asSequence().sum()

        fun <T, S : ScalarLike<S>> Sequence<T>.sumOf(selector: (T) -> S): S {
            return map(selector).reduce { acc, a -> acc + a }
        }
        fun <T, S : ScalarLike<S>> Iterable<T>.sumOf(selector: (T) -> S): S = asSequence().sumOf(selector)

        operator fun <S : ScalarLike<S>> Double.times(scalar: ScalarLike<S>): S = scalar.times(this)
        operator fun <S : ScalarLike<S>> Double.div(scalar: ScalarLike<S>): S = scalar.div(this)

        operator fun <S : ScalarLike<S>> Int.times(scalar: S): S = scalar.times(this)
    }
}