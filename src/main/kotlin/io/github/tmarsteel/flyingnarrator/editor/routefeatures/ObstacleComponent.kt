package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.Signal
import io.github.fenrur.signal.operators.flatMap
import io.github.fenrur.signal.operators.map
import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.editor.RouteViewModel
import io.github.tmarsteel.flyingnarrator.ui.TileImage
import io.github.tmarsteel.flyingnarrator.ui.reactive.minusAssign
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
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
import javax.swing.UIManager

class ObstacleComponent(
    routeModel: RouteViewModel,
    val obstacleModel: RouteViewModel.ObstacleModel,
) : MovableLocationOnRouteComponent(
    obstacleModel.location,
    MoveGovernor.FreelyMovable(),
) {
    val icon = iconFor(signalOf(obstacleModel.type))
    init {
        icon.subscribeOn(lifecycle) { iconImage ->
            setSize(iconImage.width, iconImage.height)
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        val borderWidth = UIManager.getInt("Button.borderWidth").coerceAtLeast(1)

        g as Graphics2D
        g.color = if (isMouseHovering) {
            UIManager.getColor("Button.hoverBackground")
        } else {
            UIManager.getColor("Button.background")
        }
        g.fillRoundRect(0, 0, width, height, BORDER_RADIUS, BORDER_RADIUS)
        g.stroke = BasicStroke(borderWidth.toFloat())
        g.color = if (isMouseHovering) {
            UIManager.getColor("Button.hoverBorderColor")
        } else {
            UIManager.getColor("Button.borderColor")
        }
        g.drawRoundRect(0, 0, width - borderWidth, height - borderWidth, BORDER_RADIUS, BORDER_RADIUS)

        g.drawImage(icon.value, 0, 0, null)
    }

    init {
        componentPopupMenu = JPopupMenu()
        if (obstacleModel.type is RouteViewModel.ObstacleModel.Type.Chicane) {
            val entryLeftItem = JRadioButtonMenuItem("entry on the left").apply {
                addActionListener {
                    obstacleModel.type.entrySide.value = RouteViewModel.ObstacleModel.Type.Chicane.EntrySide.LEFT
                }
            }
            val entryRightItem = JRadioButtonMenuItem("entry on the right").apply {
                addActionListener {
                    obstacleModel.type.entrySide.value = RouteViewModel.ObstacleModel.Type.Chicane.EntrySide.RIGHT
                }
            }
            val entrySideUnknownItem = JRadioButtonMenuItem("entry side not known").apply {
                addActionListener {
                    obstacleModel.type.entrySide.value = RouteViewModel.ObstacleModel.Type.Chicane.EntrySide.UNSPECIFIED
                }
            }
            val buttonGroup = ButtonGroup()
            listOf(entryLeftItem, entryRightItem, entrySideUnknownItem).forEach {
                buttonGroup.add(it)
                componentPopupMenu.add(it)
            }
            componentPopupMenu.addSeparator()

            obstacleModel.type.entrySide.subscribeOn(lifecycle) { side ->
                when (side) {
                    RouteViewModel.ObstacleModel.Type.Chicane.EntrySide.LEFT -> {
                        entryLeftItem.isSelected = true
                        entryRightItem.isSelected = false
                        entrySideUnknownItem.isSelected = false
                    }
                    RouteViewModel.ObstacleModel.Type.Chicane.EntrySide.RIGHT -> {
                        entryLeftItem.isSelected = false
                        entryRightItem.isSelected = true
                        entrySideUnknownItem.isSelected = false
                    }
                    RouteViewModel.ObstacleModel.Type.Chicane.EntrySide.UNSPECIFIED -> {
                        entryLeftItem.isSelected = false
                        entryRightItem.isSelected = false
                        entrySideUnknownItem.isSelected = true
                    }
                }
            }
        }
        componentPopupMenu.add(JMenuItem("Delete").also { item ->
            item.addActionListener {
                routeModel.obstacles -= obstacleModel
            }
        })
    }

    private var isMouseHovering = false

    override fun mouseEntered(e: MouseEvent?) {
        isMouseHovering = true
        repaint()
    }

    override fun mouseExited(e: MouseEvent?) {
        isMouseHovering = false
        repaint()
    }

    private val deleteKeyDispatcher = KeyEventDispatcher { e: KeyEvent ->
        if (!isMouseHovering) return@KeyEventDispatcher false
        if (e.id != KeyEvent.KEY_PRESSED || e.keyCode != KeyEvent.VK_DELETE) return@KeyEventDispatcher false
        val container = parentRouteComponent.value ?: return@KeyEventDispatcher false
        container.remove(this@ObstacleComponent)
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
        private val BORDER_RADIUS = 4
        private val ICONS by lazy {
            TileImage(
                ImageIO.read(ObstacleComponent::class.java.getResource("obstacles-24.png")!!),
                Dimension(24, 24),
            )
        }

        val CHICANE_ENTRY_LEFT_ICON get()= ICONS[0]
        val CHICANE_ENTRY_RIGHT_ICON get()= ICONS[1]
        val CREST_ICON get()= ICONS[2]
        val DIP_ICON get()= ICONS[3]
        val JUMP_ICON get()= ICONS[4]
        val NARROW_ICON get()= ICONS[5]
        val WIDE_ICON get()= ICONS[6]

        fun iconFor(type: Signal<RouteViewModel.ObstacleModel.Type>): Signal<BufferedImage> = type.flatMap { type ->
            when (type) {
                is RouteViewModel.ObstacleModel.Type.Chicane -> type.entrySide.map { when (it) {
                    RouteViewModel.ObstacleModel.Type.Chicane.EntrySide.LEFT,
                    RouteViewModel.ObstacleModel.Type.Chicane.EntrySide.UNSPECIFIED -> CHICANE_ENTRY_LEFT_ICON
                    RouteViewModel.ObstacleModel.Type.Chicane.EntrySide.RIGHT -> CHICANE_ENTRY_RIGHT_ICON
                }}
                RouteViewModel.ObstacleModel.Type.Crest -> signalOf(CREST_ICON)
                RouteViewModel.ObstacleModel.Type.Dip -> signalOf(DIP_ICON)
                RouteViewModel.ObstacleModel.Type.Jump -> signalOf(JUMP_ICON)
                RouteViewModel.ObstacleModel.Type.Narrows -> signalOf(NARROW_ICON)
                RouteViewModel.ObstacleModel.Type.Widens -> signalOf(WIDE_ICON)
            }
        }
    }
}
