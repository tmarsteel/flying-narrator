package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.MutableSignal
import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.fenrur.signal.operators.flatMap
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.editor.PointOnTrackEditHandle
import io.github.tmarsteel.flyingnarrator.editor.RouteEditorViewModel
import io.github.tmarsteel.flyingnarrator.editor.transform
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

abstract class ImageUIRouteFeature(
    val routeModel: RouteEditorViewModel,
    initialImage: BufferedImage,
    val location: Signal<RouteEditorViewModel.PreciseLocation>,
) : UIRouteFeature() {
    val image = mutableSignalOf(initialImage)

    private val editableLocation: MutableSignal<RouteEditorViewModel.PreciseLocation>? = location as MutableSignal<RouteEditorViewModel.PreciseLocation>?

    override val isSelectable: Boolean get()= editableLocation != null

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

    private val editHandle by lazy {
        object : PointOnTrackEditHandle(
            routeModel,
            editableLocation ?: error("the location is not editable, why was the edit handle requested?"),
            EditGovernor.FreelyMovable,
        ) {
            init {
                image.subscribeOn(lifecycle) { image ->
                    setSize(image.width, image.height)
                }
                componentPopupMenu = this@ImageUIRouteFeature.popupMenu
            }
            override fun paintComponent(g: Graphics) {
                g.drawImage(image.value, 0, 0, null)
            }
        }
    }

    init {
        selected.subscribeOn(lifecycle) { isSelected ->
            if (isSelected) {
                parent.value?.add(editHandle)
            } else {
                parent.value?.remove(editHandle)
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