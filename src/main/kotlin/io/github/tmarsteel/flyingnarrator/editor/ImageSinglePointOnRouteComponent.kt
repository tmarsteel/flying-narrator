package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

abstract class ImageSinglePointOnRouteComponent(
    val image: BufferedImage,
    private val viewModel: RouteEditorViewModel,
    val point: Vector3,
    editGovernor: EditableSinglePointOnRouteComponent.EditGovernor,
) : RouteBoundComponent {
    init {
        check(editGovernor is EditableSinglePointOnRouteComponent.EditGovernor.NotEditable) {
            "not supported yet"
        }
    }

    override fun shouldCapture(pointedTrackLocation: Vector3): Boolean {
        // TODO: implement editing for e.g. jumps
        return false
    }

    override var isHovered: Boolean = false
    override val isSelectable: Boolean = false
    override var routeTransform: AffineTransform = AffineTransform()

    override fun paint(g: Graphics2D) {
        val centerPoint = routeTransform.transform(point, null)
        g.translate(centerPoint.x, centerPoint.y)
        g.translate(-image.width / 2.0, -image.height / 2.0)
        g.drawImage(image, 0, 0, null)
    }
}