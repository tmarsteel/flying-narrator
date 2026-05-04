package io.github.tmarsteel.flyingnarrator.ui.reactive

import javax.swing.JComponent

abstract class ReactiveJComponent : JComponent() {
    val lifecycle = ReactiveComponentLifecycle()

    override fun addNotify() {
        super.addNotify()
        lifecycle.onComponentMounted()
    }

    override fun removeNotify() {
        lifecycle.onComponentUnmounted()
        super.removeNotify()
    }
}