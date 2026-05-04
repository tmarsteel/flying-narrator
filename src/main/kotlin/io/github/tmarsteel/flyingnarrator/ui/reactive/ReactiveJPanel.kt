package io.github.tmarsteel.flyingnarrator.ui.reactive

import javax.swing.JPanel

abstract class ReactiveJPanel : JPanel() {
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