package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.mutableSignalOf
import io.github.tmarsteel.flyingnarrator.editor.PointOnTrackEditHandle
import io.github.tmarsteel.flyingnarrator.editor.RouteEditorViewModel
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.unit.Distance
import java.awt.Graphics
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.ButtonGroup
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem

class ObstacleUIRouteFeature(
    viewModel: RouteEditorViewModel,
    val obstacleModel: RouteEditorViewModel.ObstacleModel,
) : PointOnTrackEditHandle(
    viewModel,
    obstacleModel.location,
    EditGovernor.FreelyMovable,
) {
    constructor(
        viewModel: RouteEditorViewModel,
        distanceAlongTrack: Distance,
        type: RouteEditorViewModel.ObstacleModel.Type = RouteEditorViewModel.ObstacleModel.Type.CREST,
    ) : this(
        viewModel,
        RouteEditorViewModel.ObstacleModel(
            mutableSignalOf(viewModel.findPreciseLocation(distanceAlongTrack)!!),
            mutableSignalOf(type),
        ),
    )

    init {
        val initialIcon = iconFor(obstacleModel.type.value)
        setSize(initialIcon.width, initialIcon.height)

        obstacleModel.type.subscribeOn(lifecycle) { type ->
            val icon = iconFor(type)
            setSize(icon.width, icon.height)
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        g.drawImage(iconFor(obstacleModel.type.value), 0, 0, null)
    }

    private val typeMenuItems = RouteEditorViewModel.ObstacleModel.Type.entries.associateWith { type ->
        JRadioButtonMenuItem(type.name.lowercase().replace('_', ' '))
    }
    init {
        componentPopupMenu = JPopupMenu().apply {
            val buttonGroup = ButtonGroup()
            RouteEditorViewModel.ObstacleModel.Type.entries.forEach { type ->
                val item = typeMenuItems[type]!!
                buttonGroup.add(item)
                add(item)
            }
            addSeparator()
            add(JMenuItem("Delete").also { item ->
                item.addActionListener {
                    val container = parentRouteComponent.value ?: return@addActionListener
                    container.remove(this@ObstacleUIRouteFeature)
                    container.revalidate()
                    container.repaint()
                }
            })
        }
        obstacleModel.type.subscribeOn(lifecycle) { type ->
            typeMenuItems[type]!!.isSelected = true
        }
        typeMenuItems.forEach { (type, item) ->
            item.addActionListener {
                obstacleModel.type.value = type
            }
        }
    }

    private var isMouseHovering = false

    override fun mouseEntered(e: MouseEvent?) {
        isMouseHovering = true
    }

    override fun mouseExited(e: MouseEvent?) {
        isMouseHovering = false
    }

    private val deleteKeyDispatcher = KeyEventDispatcher { e: KeyEvent ->
        if (!isMouseHovering) return@KeyEventDispatcher false
        if (e.id != KeyEvent.KEY_PRESSED || e.keyCode != KeyEvent.VK_DELETE) return@KeyEventDispatcher false
        val container = parentRouteComponent.value ?: return@KeyEventDispatcher false
        container.remove(this@ObstacleUIRouteFeature)
        container.revalidate()
        container.repaint()
        true
    }

    override fun addNotify() {
        super.addNotify()
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(deleteKeyDispatcher)
    }

    override fun removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(deleteKeyDispatcher)
        super.removeNotify()
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

        private fun iconFor(type: RouteEditorViewModel.ObstacleModel.Type): BufferedImage = when (type) {
            RouteEditorViewModel.ObstacleModel.Type.CREST -> CREST_TILE.getSubimage(0, 0, 24, 24)
            RouteEditorViewModel.ObstacleModel.Type.DIP -> DIP_TILE.getSubimage(0, 0, 24, 24)
            RouteEditorViewModel.ObstacleModel.Type.JUMP -> JUMP_TILE.getSubimage(0, 0, 24, 24)
            RouteEditorViewModel.ObstacleModel.Type.TUNNEL -> TUNNEL_TILE.getSubimage(0, 0, 24, 24)
            RouteEditorViewModel.ObstacleModel.Type.NARROWS -> NARROWS_WIDENS_TILE.getSubimage(0, 0, 24, 24)
            RouteEditorViewModel.ObstacleModel.Type.WIDENS -> NARROWS_WIDENS_TILE.getSubimage(0, 24, 24, 24)
        }
    }
}
