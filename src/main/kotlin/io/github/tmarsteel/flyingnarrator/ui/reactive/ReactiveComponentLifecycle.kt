package io.github.tmarsteel.flyingnarrator.ui.reactive

import io.github.fenrur.signal.impl.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class ReactiveComponentLifecycle {
    private val mounted = AtomicBoolean(false)
    private val lifecycleAwares = CopyOnWriteArrayList<LifecycleAware>()

    fun notifyAdd() {
        if (!mounted.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }

        lifecycleAwares.forEach {
            it.onComponentMounted()
        }
    }

    fun notifyRemove() {
        if (!mounted.compareAndSet(expectedValue = true, newValue = false)) {
            return
        }

        lifecycleAwares.forEach {
            it.onComponentUnmounted()
        }
    }

    fun addLifecycleAware(obj: LifecycleAware) {
        lifecycleAwares += obj
        if (mounted.load()) {
            obj.onComponentMounted()
        }
    }

    interface LifecycleAware {
        fun onComponentMounted()
        fun onComponentUnmounted()
    }
}