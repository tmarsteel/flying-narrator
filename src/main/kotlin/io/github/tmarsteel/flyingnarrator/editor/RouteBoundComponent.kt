package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.withTransform
import java.awt.Component
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import javax.swing.JToolTip

sealed interface RouteBoundComponent {
    /**
     * Called whenever the mouse moves in the parent [RouteComponent]
     * @param pointedTrackLocation where the mouse points, in the track coordinate space, but with [Vector3.z] being `0`
     * because [RouteComponent] is 2-dimensional top-down
     * @return whether this component should be hovered at the given mouse position
     */
    fun tryClaimHover(pointedTrackLocation: Vector3): Boolean

    /**
     * Is set to `true` iff [tryClaimHover] returned true and there is no other component that should rather have
     * the hover. Is set to `false` if hover is lost to another element or [tryClaimHover] starts returning false.
     */
    var isHovered: Boolean

    val isSelectable: Boolean

    /**
     * translates from the routes coordinate space to that of the [RouteComponent], is set by the parent
     * [RouteComponent]. Can be used in [paint] with [withTransform]
     */
    var routeTransform: AffineTransform

    /**
     * Called when the user selects this component (e.g., by clicking on it).
     * @param addComponent components passed to this will become part of the [RouteComponent]s children as long as
     * this component is selected
     */
    fun onSelected(addComponent: (Component) -> Unit) {}

    /**
     * Visualize this element by drawing on top of the current track view.
     * @param g paints to the [RouteComponent], its swing/pixel coordinate space
     */
    fun paint(g: Graphics2D)

    val tooltip: JToolTip? get()= null
}