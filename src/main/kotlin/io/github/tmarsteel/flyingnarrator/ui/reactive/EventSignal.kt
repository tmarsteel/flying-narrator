package io.github.tmarsteel.flyingnarrator.ui.reactive

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.SubscribeListener
import io.github.fenrur.signal.UnSubscriber
import io.github.fenrur.signal.impl.CopyOnWriteArrayList
import io.github.fenrur.signal.impl.DirtyMarkable
import io.github.fenrur.signal.impl.EffectNode
import io.github.fenrur.signal.impl.SignalGraph
import io.github.fenrur.signal.impl.SourceSignalNode
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference

abstract class EventSignal<T>() : Signal<T>, SourceSignalNode {
    protected abstract fun getCurrentValue(): T
    protected abstract fun registerListener(onNewValue: (T) -> Unit): UnSubscriber

    private val closed = AtomicBoolean(false)
    private val currentValue = AtomicReference<T?>(null)

    private val sourceListenerRegistrationMutex = Any()
    @Volatile
    private var isSourceListenerRegistered: Boolean = false
    @Volatile
    private var unregisterSourceListener: UnSubscriber? = null

    private val signalListeners = CopyOnWriteArrayList<SubscribeListener<T>>()
    private val targets = CopyOnWriteArrayList<DirtyMarkable>()

    override fun subscribe(listener: SubscribeListener<T>): UnSubscriber {
        if (isClosed) return {}
        listener(Result.success(value))
        signalListeners += listener
        assureListenerRegistered()
        return { this.unsubscribe(listener) }
    }

    override val isClosed: Boolean
        get() = closed.load()

    override val value: T
        get() {
            if (!isSourceListenerRegistered) {
                return getCurrentValue()
            }

            return currentValue.load() ?: getCurrentValue()
        }

    override fun addTarget(target: DirtyMarkable) {
        targets += target
    }

    override fun removeTarget(target: DirtyMarkable) {
        targets -= target
    }

    override val version: Long
        get() = value.hashCode().toLong()

    override fun close() {
        if (closed.compareAndSet(expectedValue = false, newValue = true)) {
            signalListeners.clear()
            targets.clear()
        }
    }

    private fun assureListenerRegistered() {
        if (isSourceListenerRegistered) {
            return
        }
        synchronized(sourceListenerRegistrationMutex) {
            if (isSourceListenerRegistered || (signalListeners.isEmpty() && targets.isEmpty())) {
                return
            }

            unregisterSourceListener = registerListener(this::onNewValue)
            isSourceListenerRegistered = true
        }
    }

    private fun onNewValue(newValue: T) {
        if (isClosed) {
            return
        }

        val oldValue = currentValue.exchange(newValue)
        if (oldValue == newValue) {
            return
        }

        SignalGraph.startBatch()
        try {
            for (target in targets) {
                target.markDirty()
            }

            if (signalListeners.isNotEmpty()) {
                scheduleListenerNotification(newValue)
            }
        } finally {
            SignalGraph.endBatch()
        }
    }

    private fun scheduleListenerNotification(newValue: T) {
        val effect = object : EffectNode {
            private val pending = AtomicBoolean(false)

            override fun markPending(): Boolean = pending.compareAndSet(false, true)

            override fun execute() {
                pending.store(false)
                if (!isClosed) {
                    // Read current value at execution time for consistency
                    val currentValue = this@EventSignal.value
                    for (listener in signalListeners) {
                        try {
                            listener(Result.success(currentValue))
                        } catch (_: Throwable) {
                            // ignore
                        }
                    }
                }
            }
        }
        SignalGraph.scheduleEffect(effect)
    }

    private fun unsubscribe(listener: SubscribeListener<T>) {
        signalListeners -= listener
        if (signalListeners.isEmpty() && targets.isEmpty()) {
            synchronized(sourceListenerRegistrationMutex) {
                if (signalListeners.isEmpty() && targets.isEmpty()) {
                    isSourceListenerRegistered = false
                    unregisterSourceListener?.invoke()
                    unregisterSourceListener = null
                }
            }
        }
    }
}