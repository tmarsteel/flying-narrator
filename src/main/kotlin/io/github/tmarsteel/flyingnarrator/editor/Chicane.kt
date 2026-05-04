package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.unit.Distance
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class Chicane(
    routeModel: RouteEditorViewModel,
    val chicaneModel: RouteEditorViewModel.ChicaneModel,
) : ImageSinglePointOnRouteComponent(
    routeModel,
    iconFor(chicaneModel.entry.value, false),
    chicaneModel.location,
) {
    constructor(
        routeModel: RouteEditorViewModel,
        distanceAlongTrack: Distance,
        entry: RouteEditorViewModel.ChicaneModel.Entry = RouteEditorViewModel.ChicaneModel.Entry.UNSPECIFIED
    ) : this(
        routeModel,
        RouteEditorViewModel.ChicaneModel(
            mutableSignalOf(routeModel.findPreciseLocation(distanceAlongTrack)!!),
            mutableSignalOf(entry)
        )
    )

    init {
        combine(chicaneModel.entry, selected) { entry, isSelected ->
            image.value = iconFor(entry, isSelected)
        }.subscribeOn(lifecycle) {}
    }

    override val zIndex: Int = 10

    companion object {
        private val TILE by lazy {
            ImageIO.read(StartMarker::class.java.getResource("chicane-24-tiled.png")!!)
        }
        val ICON_ENTRY_LEFT_DEFAULT by lazy {
            TILE.getSubimage(0, 0, 24, 24)
        }
        val ICON_ENTRY_LEFT_SELECTED by lazy {
            TILE.getSubimage(24, 0, 24, 24)
        }
        val ICON_ENTRY_RIGHT_DEFAULT by lazy {
            TILE.getSubimage(0, 24, 24, 24)
        }
        val ICON_ENTRY_RIGHT_SELECTED by lazy {
            TILE.getSubimage(24, 24, 24, 24)
        }

        private fun iconFor(entry: RouteEditorViewModel.ChicaneModel.Entry, selected: Boolean): BufferedImage = when(entry) {
            RouteEditorViewModel.ChicaneModel.Entry.LEFT,
            RouteEditorViewModel.ChicaneModel.Entry.UNSPECIFIED -> if (selected) ICON_ENTRY_LEFT_SELECTED else ICON_ENTRY_LEFT_DEFAULT
            RouteEditorViewModel.ChicaneModel.Entry.RIGHT -> if (selected) ICON_ENTRY_RIGHT_SELECTED else ICON_ENTRY_RIGHT_DEFAULT
        }
    }
}