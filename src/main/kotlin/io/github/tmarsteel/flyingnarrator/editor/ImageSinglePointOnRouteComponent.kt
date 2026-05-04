package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import java.awt.Graphics2D
import java.awt.image.BufferedImage

abstract class ImageSinglePointOnRouteComponent(
    val image: BufferedImage,
    private val viewModel: RouteEditorViewModel,
    val point: Vector3,
    editGovernor: SinglePointOnTrackEditHandle.EditGovernor,
) : RouteBoundComponent() {
    init {
        check(editGovernor is SinglePointOnTrackEditHandle.EditGovernor.NotEditable) {
            "not supported yet"
        }
    }

    override fun shouldCapture(pointedTrackLocation: Vector3): Boolean {
        // TODO: implement editing for e.g. jumps
        return false
    }

    override var isHovered: Boolean = false
    override val isSelectable: Boolean = false

    override fun paint(g: Graphics2D) {
        val centerPoint = parent.value!!.routeTransform.value.transform(point, null)
        g.translate(centerPoint.x, centerPoint.y)
        g.translate(-image.width / 2.0, -image.height / 2.0)
        g.drawImage(image, 0, 0, null)
    }
}