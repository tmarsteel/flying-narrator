package io.github.tmarsteel.flyingnarrator.ui.reactive

import javax.swing.JComponent

abstract class ReactiveJComponent : JComponent() {
    val lifecycle = ReactiveComponentLifecycle()

    override fun addNotify() {
        super.addNotify()
        lifecycle.notifyAdd()
    }

    override fun removeNotify() {
        lifecycle.notifyRemove()
        super.removeNotify()
    }
}