package io.github.tmarsteel.flyingnarrator.utils

import kotlin.reflect.KProperty

class DeriveFromDelegate<T, D : Any?>(
    val dependencies: Array<out () -> D>,
    val compute: (List<D>) -> T,
) {
    private var initialized = false
    private var dependencyValuesOfCache: ArrayList<D> = ArrayList()
    private var cached: Any? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        assureFresh()
        @Suppress("UNCHECKED_CAST")
        return cached as T
    }

    private val refreshLock = Any()
    private var isRefreshing = false
    private fun assureFresh() {
        val dependencyValues = collectDependencyValues()
        if (initialized && dependencyValuesOfCache == dependencyValues) {
            return
        }

        synchronized(refreshLock) {
            if (isRefreshing) {
                throw IllegalStateException("Circular dependencies")
            }
            isRefreshing = true
            try {
                val newValue = compute(dependencyValues)
                cached = newValue
                dependencyValuesOfCache = dependencyValues
                initialized = true
            }
            finally {
                isRefreshing = false
            }
        }
    }

    private fun collectDependencyValues(): ArrayList<D> {
        return dependencies.mapTo(ArrayList(dependencies.size)) { it() }
    }

    companion object {
        fun <T, D> deriveFrom(vararg dependencies: () -> D, compute: (List<D>) -> T): DeriveFromDelegate<T, D> = DeriveFromDelegate(dependencies, compute)
    }
}