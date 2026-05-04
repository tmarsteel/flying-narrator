package io.github.tmarsteel.flyingnarrator.ui.reactive

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.UnSubscriber

class LifecycleSignalSubscription<T>(
    val signal: Signal<T>,
    val consumer: (Result<T>) -> Unit,
): ReactiveComponentLifecycle.LifecycleAware {
    @Volatile
    private var unsubscribe: UnSubscriber? = null
    override fun onComponentMounted() {
        synchronized(this) {
            check(unsubscribe == null) { "double activation" }
            unsubscribe = signal.subscribe(consumer)
        }
    }

    override fun onComponentUnmounted() {
        synchronized(this) {
            unsubscribe?.invoke()
            unsubscribe = null
        }
    }
}