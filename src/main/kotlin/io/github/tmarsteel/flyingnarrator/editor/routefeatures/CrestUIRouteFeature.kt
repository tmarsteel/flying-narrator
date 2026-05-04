package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.mutableSignalOf
import io.github.tmarsteel.flyingnarrator.editor.RouteEditorViewModel
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.unit.Distance
import javax.imageio.ImageIO

class CrestUIRouteFeature(
    routeModel: RouteEditorViewModel,
    val crestModel: RouteEditorViewModel.CrestModel,
) : ImageUIRouteFeature(
    routeModel,
    ICON_DEFAULT,
    crestModel.location,
) {
    constructor(
        routeModel: RouteEditorViewModel,
        distanceAlongTrack: Distance,
    ) : this(
        routeModel,
        RouteEditorViewModel.CrestModel(
            mutableSignalOf(routeModel.findPreciseLocation(distanceAlongTrack)!!),
        ),
    )

    init {
        selected.subscribeOn(lifecycle) { isSelected ->
            image.value = if (isSelected) ICON_SELECTED else ICON_DEFAULT
        }
    }

    override val zIndex: Int = 10

    companion object {
        private val TILE by lazy {
            ImageIO.read(ChicaneUIRouteFeature::class.java.getResource("crest-24-tiled.png")!!)
        }
        val ICON_DEFAULT by lazy { TILE.getSubimage(0, 0, 24, 24) }
        val ICON_SELECTED by lazy { TILE.getSubimage(24, 0, 24, 24) }
    }
}
