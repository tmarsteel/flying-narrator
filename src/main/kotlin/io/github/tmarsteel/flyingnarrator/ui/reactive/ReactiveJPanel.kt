package io.github.tmarsteel.flyingnarrator.ui.reactive

import javax.swing.JPanel

abstract class ReactiveJPanel : JPanel() {
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