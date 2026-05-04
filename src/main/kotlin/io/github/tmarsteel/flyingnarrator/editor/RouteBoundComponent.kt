package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import java.awt.Component
import java.awt.Graphics2D
import javax.swing.JToolTip

sealed interface RouteBoundComponent {
    fun onMounted(parent: RouteComponent) {}
    fun onUnmounted() {}

    /**
     * Called to determine whether a mouse position in the parent [RouteComponent] belongs to this [RouteBoundComponent].
     * @param pointedTrackLocation where the mouse points, in the track coordinate space, but with [Vector3.z] being `0`
     * because [RouteComponent] is 2-dimensional top-down
     * @return whether the mouse interaction at this point belongs to this [RouteBoundComponent]
     */
    fun shouldCapture(pointedTrackLocation: Vector3): Boolean

    /**
     * Is set to `true` iff [shouldCapture] returned true and there is no other component that should rather have
     * the hover. Is set to `false` if hover is lost to another element or [shouldCapture] starts returning false.
     */
    var isHovered: Boolean

    val isSelectable: Boolean

    /**
     * Called when the user selects this component (e.g., by clicking on it).
     * @param addComponent components passed to this will become part of the [RouteComponent]s children as long as
     * this component is selected
     */
    fun onSelected(addComponent: (Component) -> Unit) {}

    fun onDeselected() {}

    /**
     * Visualize this element by drawing on top of the current track view.
     * @param g paints to the [RouteComponent], its swing/pixel coordinate space
     */
    fun paint(g: Graphics2D)

    val tooltip: JToolTip? get()= null
}