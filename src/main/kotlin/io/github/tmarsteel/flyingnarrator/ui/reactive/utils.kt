package io.github.tmarsteel.flyingnarrator.ui.reactive

import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.operators.pairwise
import io.github.fenrur.signal.operators.scan

fun <T> Signal<T>.subscribeOn(lifecycle: ReactiveComponentLifecycle, consumer: (T) -> Unit) {
    lifecycle.addLifecycleAware(LifecycleSignalSubscription(this, { consumer(it.getOrThrow()) }))
}

/**
 * Emits changes to the list in the signal. Always starts with one [SetDelta] that has all the elements of the first
 * [Set] in [SetDelta.added].
 */
fun <T> Signal<Set<T>>.changesWithInitial(): Signal<SetDelta<T>> {
    return this
        .scan(emptySet<T>(), { _, new -> new })
        .pairwise()
        .map { (previous, new) ->
            val added = new.filter { it !in previous }
            val removed = previous.filter { it !in new }
            SetDelta(added, removed)
        }
}
data class SetDelta<T>(
    val added: List<T>,
    val removed: List<T>,
)

operator fun <T> MutableSignal<Set<T>>.plusAssign(element: T) {
    this.update { it + element }
}

operator fun <T> MutableSignal<Set<T>>.minusAssign(element: T) {
    this.update { it - element }
}