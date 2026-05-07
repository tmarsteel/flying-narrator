package io.github.tmarsteel.flyingnarrator.ui

import javax.swing.UIManager
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class UIDefaultsDelegate<T>(
    val key: String,
    val clazz: Class<T>,
) : ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val value = UIManager.get(key)
        check(clazz.isInstance(value)) { "expected $clazz, got $value" }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}