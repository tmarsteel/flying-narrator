package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import java.awt.Graphics2D

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

    /**
     * Visualize this element by drawing on top of the current track view.
     * @param g takes draw commands in route coordinate space
     */
    fun paint(g: Graphics2D)
}