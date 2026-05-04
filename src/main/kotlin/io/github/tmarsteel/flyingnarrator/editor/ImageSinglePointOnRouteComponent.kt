package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.operators.flatMap
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

abstract class ImageSinglePointOnRouteComponent(
    val routeModel: RouteEditorViewModel,
    initialImage: BufferedImage,
    initialLocation: RouteEditorViewModel.PreciseLocation,
) : RouteBoundComponent() {
    val image = mutableSignalOf(initialImage)
    val location = mutableSignalOf(initialLocation)

    protected abstract val editGovernor: SinglePointOnTrackEditHandle.EditGovernor

    override val isSelectable: Boolean get()= editGovernor is SinglePointOnTrackEditHandle.EditGovernor.Editable

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

    private var editHandle: SinglePointOnTrackEditHandle? = null
    init {
        selected.subscribeOn(lifecycle) { isSelected ->
            if (isSelected) {
                if (editHandle == null) {
                    editHandle = object : SinglePointOnTrackEditHandle(
                        routeModel,
                        location.value,
                        Snapping.FreeMovement,
                        editGovernor,
                    ) {
                        init {
                            image.subscribeOn(lifecycle) { image ->
                                setSize(image.width, image.height)
                            }
                        }
                        override fun paintComponent(g: Graphics) {
                            g.drawImage(image.value, 0, 0, null)
                        }
                    }
                }
                parent.value?.add(editHandle!!)
            } else {
                editHandle?.let { parent.value?.remove(it) }
            }
        }
    }

    override fun paint(g: Graphics2D) {
        if (selected.value) {
            // in this case, let the edit handle take the rendering
            return
        }

        val image = image.value
        val selfShape = selfShapeInParentPixelSpace.value

        g.translate(selfShape.x, selfShape.y)
        g.drawImage(image, 0, 0, null)
    }
}