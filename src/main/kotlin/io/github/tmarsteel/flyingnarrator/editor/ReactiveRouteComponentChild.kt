package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.tmarsteel.flyingnarrator.ui.reactive.ReactiveJComponent

/*+
 * combines [ReactiveJComponent] and the semantics that the parent of this component must always be a
 * [RouteComponent]
 */
abstract class ReactiveRouteComponentChild : ReactiveJComponent() {
    private val _parent = mutableSignalOf<RouteComponent?>(null)
    protected val parentRouteComponent: Signal<RouteComponent?> = _parent

    override fun addNotify() {
        _parent.value = super.parent as? RouteComponent
            ?: error("this component must be a child of RouteComponent, but found parent ${super.parent}")
        super.addNotify()
    }

    override fun removeNotify() {
        super.removeNotify()
        _parent.value = null
    }
}