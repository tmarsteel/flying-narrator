package io.github.tmarsteel.flyingnarrator.ui.reactive

import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.operators.combineAll
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.operators.pairwise
import io.github.fenrur.signal.operators.scan
import io.github.fenrur.signal.operators.switchMap
import java.util.WeakHashMap
import javax.swing.JComponent

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

fun <T, R> Signal<Iterable<T>>.mapFlatten(
    transform: (T) -> Signal<R>,
): Signal<List<R>> {
    return switchMap { es ->
        combineAll(*es.map(transform).toTypedArray())
    }
}

fun <T> Signal<Collection<T>>.switchAny(predicate: (T) -> Signal<Boolean>): Signal<Boolean> {
    return mapFlatten(predicate).map { bs -> bs.any { it } }
}

/**
 * While [lifecycle] is active, translates the deltas from `this` to swing state.
 * @param onAdded called for new elements, e.g. [javax.swing.JComponent.add]
 * @param onRemoved called for removed elements, e.g. [javax.swing.JComponent.remove]
 * @param transform transform viewmodel state [T] to a stateful object [M]
 */
fun <T, M> Signal<Set<T>>.bridgeToStatefulOn(
    lifecycle: ReactiveComponentLifecycle,
    onAdded: (M) -> Unit,
    onRemoved: (M) -> Unit,
    transform: (T) -> M,
) {
    val modelToViewObject = WeakHashMap<T, M>()
    this.changesWithInitial().subscribeOn(lifecycle) { delta ->
        delta.added.forEach { newModel ->
            modelToViewObject.compute(newModel) { newModel, oldViewState ->
                oldViewState ?: transform(newModel).also { onAdded(it) }
            }
        }
        delta.removed
            .mapNotNull(modelToViewObject::get)
            .forEach(onRemoved)
    }
}

fun <T> Signal<Set<T>>.bridgeToChildComponents(
    parent: ReactiveJComponent,
    transform: (T) -> JComponent
) {
    bridgeToStatefulOn(parent.lifecycle, parent::add, parent::remove, transform)
}