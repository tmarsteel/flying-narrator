package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.mutableSignalOf
import io.github.fenrur.signal.operators.combine
import io.github.tmarsteel.flyingnarrator.editor.RouteEditorViewModel
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.unit.Distance
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.ButtonGroup
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem

class ObstacleUIRouteFeature(
    routeModel: RouteEditorViewModel,
    val obstacleModel: RouteEditorViewModel.ObstacleModel,
) : ImageUIRouteFeature(
    routeModel,
    iconFor(obstacleModel.type.value, false),
    obstacleModel.location,
) {
    constructor(
        routeModel: RouteEditorViewModel,
        distanceAlongTrack: Distance,
        type: RouteEditorViewModel.ObstacleModel.Type = RouteEditorViewModel.ObstacleModel.Type.CREST,
    ) : this(
        routeModel,
        RouteEditorViewModel.ObstacleModel(
            mutableSignalOf(routeModel.findPreciseLocation(distanceAlongTrack)!!),
            mutableSignalOf(type),
        ),
    )

    init {
        combine(obstacleModel.type, selected) { type, isSelected ->
            image.value = iconFor(type, isSelected)
        }.subscribeOn(lifecycle) {}
    }

    override val zIndex: Int = 10

    private val typeMenuItems = RouteEditorViewModel.ObstacleModel.Type.entries.associateWith { type ->
        JRadioButtonMenuItem(type.name.lowercase().replace('_', ' '))
    }
    override val popupMenu: JPopupMenu = JPopupMenu().apply {
        val buttonGroup = ButtonGroup()
        RouteEditorViewModel.ObstacleModel.Type.entries.forEach { type ->
            val item = typeMenuItems[type]!!
            buttonGroup.add(item)
            add(item)
        }
    }

    init {
        obstacleModel.type.subscribeOn(lifecycle) { type ->
            typeMenuItems[type]!!.isSelected = true
        }
        typeMenuItems.forEach { (type, item) ->
            item.addActionListener {
                obstacleModel.type.value = type
            }
        }
    }

    companion object {
        private val CREST_TILE by lazy {
            ImageIO.read(ObstacleUIRouteFeature::class.java.getResource("crest-24-tiled.png")!!)
        }
        private val DIP_TILE by lazy {
            ImageIO.read(ObstacleUIRouteFeature::class.java.getResource("dip-24-tiled.png")!!)
        }
        private val JUMP_TILE by lazy {
            ImageIO.read(ObstacleUIRouteFeature::class.java.getResource("jump-24-tiled.png")!!)
        }
        private val TUNNEL_TILE by lazy {
            ImageIO.read(ObstacleUIRouteFeature::class.java.getResource("tunnel-24-tiled.png")!!)
        }
        private val NARROWS_WIDENS_TILE by lazy {
            ImageIO.read(ObstacleUIRouteFeature::class.java.getResource("narrows_widens-24-tiled.png")!!)
        }

        private fun iconFor(type: RouteEditorViewModel.ObstacleModel.Type, selected: Boolean): BufferedImage {
            val x = if (selected) 24 else 0
            return when (type) {
                RouteEditorViewModel.ObstacleModel.Type.CREST -> CREST_TILE.getSubimage(x, 0, 24, 24)
                RouteEditorViewModel.ObstacleModel.Type.DIP -> DIP_TILE.getSubimage(x, 0, 24, 24)
                RouteEditorViewModel.ObstacleModel.Type.JUMP -> JUMP_TILE.getSubimage(x, 0, 24, 24)
                RouteEditorViewModel.ObstacleModel.Type.TUNNEL -> TUNNEL_TILE.getSubimage(x, 0, 24, 24)
                RouteEditorViewModel.ObstacleModel.Type.NARROWS -> NARROWS_WIDENS_TILE.getSubimage(x, 0, 24, 24)
                RouteEditorViewModel.ObstacleModel.Type.WIDENS -> NARROWS_WIDENS_TILE.getSubimage(x, 24, 24, 24)
            }
        }
    }
}
