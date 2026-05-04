package io.github.tmarsteel.flyingnarrator.ui.reactive

import io.github.fenrur.signal.Signal

fun <T> Signal<T>.subscribeOn(lifecycle: ReactiveComponentLifecycle, consumer: (T) -> Unit) {
    lifecycle.addLifecycleAware(LifecycleSignalSubscription(this, { consumer(it.getOrThrow()) }))
}