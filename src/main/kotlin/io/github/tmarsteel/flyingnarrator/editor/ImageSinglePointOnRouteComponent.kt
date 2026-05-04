package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.operators.flatMap
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

abstract class ImageSinglePointOnRouteComponent(
    initialImage: BufferedImage,
    initialLocation: RouteEditorViewModel.PreciseLocation,
    editGovernor: SinglePointOnTrackEditHandle.EditGovernor,
) : RouteBoundComponent() {
    val image = mutableSignalOf(initialImage)
    val location = mutableSignalOf(initialLocation)

    override val isSelectable: Boolean = false

    private val routeTransform = parent.flatMap { it?.routeTransform ?: signalOf(AffineTransform()) }
    private val centerPointInParentPixelSpace = combine(routeTransform, location) { routeTransform, location ->
        routeTransform.transform(location.point, null)
    }

    private val selfShapeInParentPixelSpace = combine(
        centerPointInParentPixelSpace,
        image,
    ) { centerPoint, image ->
        Rectangle2D.Double(centerPoint.x - image.width / 2.0, centerPoint.y - image.height / 2.0, image.width.toDouble(), image.height.toDouble())
    }

    override fun shouldCapture(pointedTrackLocation: Vector3): Boolean {
        return selfShapeInParentPixelSpace.value.contains(
            routeTransform.value.transform(pointedTrackLocation, null)
        )
    }

    override fun paint(g: Graphics2D) {
        val image = image.value
        val selfShape = selfShapeInParentPixelSpace.value

        g.translate(selfShape.x, selfShape.y)
        g.drawImage(image, 0, 0, null)
    }
}