package io.github.tmarsteel.flyingnarrator.ui.reactive

import io.github.fenrur.signal.UnSubscriber
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import javax.swing.JComponent

class MousePositionSignal(
    val component: JComponent,
) : EventSignal<Point?>() {
    override fun getCurrentValue(): Point? {
        return component.mousePosition
    }

    override fun registerListener(onNewValue: (Point?) -> Unit): UnSubscriber {
        val listener = object : MouseMotionListener {
            override fun mouseDragged(e: MouseEvent) {

            }

            override fun mouseMoved(e: MouseEvent) {
                onNewValue(e.point)
            }
        }
        component.addMouseMotionListener(listener)
        return { component.removeMouseMotionListener(listener) }
    }
}