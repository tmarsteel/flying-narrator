package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.reactive.ReactiveComponentLifecycle
import java.awt.Graphics2D
import javax.swing.JToolTip

abstract class RouteBoundComponent {
    protected val lifecycle = ReactiveComponentLifecycle()

    private val _parent = mutableSignalOf<RouteComponent?>(null)
    protected val parent: Signal<RouteComponent?> = _parent

    open fun onMounted(parent: RouteComponent) {
        _parent.value = parent
        lifecycle.onComponentMounted()
    }

    open fun onUnmounted() {
        lifecycle.onComponentUnmounted()
        _parent.value = null
    }

    /**
     * Called to determine whether a mouse position in the parent [RouteComponent] belongs to this [RouteBoundComponent].
     * @param pointedTrackLocation where the mouse points, in the track coordinate space, but with [Vector3.z] being `0`
     * because [RouteComponent] is 2-dimensional top-down
     * @return whether the mouse interaction at this point belongs to this [RouteBoundComponent]
     */
    abstract fun shouldCapture(pointedTrackLocation: Vector3): Boolean

    /**
     * Is set to `true` iff [shouldCapture] returned true and there is no other component that should rather have
     * the hover. Is set to `false` if hover is lost to another element or [shouldCapture] starts returning false.
     */
    open var isHovered: Boolean = false

    open val isSelectable: Boolean get()= false

    /**
     * Called when the user selects this component (e.g., by clicking on it).
     */
    open fun onSelected() {}

    open fun onDeselected() {}

    /**
     * Visualize this element by drawing on top of the current track view.
     * @param g paints to the [RouteComponent], its swing/pixel coordinate space
     */
    abstract fun paint(g: Graphics2D)

    open val tooltip: JToolTip? get()= null
}