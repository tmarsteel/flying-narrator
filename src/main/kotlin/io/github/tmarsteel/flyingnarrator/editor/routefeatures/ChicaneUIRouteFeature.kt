package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.tmarsteel.flyingnarrator.editor.RouteEditorViewModel
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.unit.Distance
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem

class ChicaneUIRouteFeature(
    routeModel: RouteEditorViewModel,
    val chicaneModel: RouteEditorViewModel.ChicaneModel,
) : ImageUIRouteFeature(
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

    private val entryLeftMenuItem = JRadioButtonMenuItem("entry on the left")
    private val entryRightMenuItem = JRadioButtonMenuItem("entry on the right")
    private val entryUnspecifiedMenuItem = JRadioButtonMenuItem("entry side unknown")
    override val popupMenu: JPopupMenu = JPopupMenu().apply {
        add(entryLeftMenuItem)
        add(entryRightMenuItem)
        add(entryUnspecifiedMenuItem)
    }
    init {
        chicaneModel.entry.subscribeOn(lifecycle) { entry ->
            entryLeftMenuItem.isSelected = entry == RouteEditorViewModel.ChicaneModel.Entry.LEFT
            entryRightMenuItem.isSelected = entry == RouteEditorViewModel.ChicaneModel.Entry.RIGHT
            entryUnspecifiedMenuItem.isSelected = entry == RouteEditorViewModel.ChicaneModel.Entry.UNSPECIFIED
        }
        entryLeftMenuItem.addActionListener {
            chicaneModel.entry.value = RouteEditorViewModel.ChicaneModel.Entry.LEFT
        }
        entryRightMenuItem.addActionListener {
            chicaneModel.entry.value = RouteEditorViewModel.ChicaneModel.Entry.RIGHT
        }
        entryUnspecifiedMenuItem.addActionListener {
            chicaneModel.entry.value = RouteEditorViewModel.ChicaneModel.Entry.UNSPECIFIED
        }
    }

    companion object {
        private val TILE by lazy {
            ImageIO.read(StartUIRouteFeature::class.java.getResource("chicane-24-tiled.png")!!)
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