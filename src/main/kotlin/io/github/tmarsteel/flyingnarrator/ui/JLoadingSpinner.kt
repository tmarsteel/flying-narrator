package io.github.tmarsteel.flyingnarrator.ui

import io.github.tmarsteel.flyingnarrator.unit.Angle.Companion.degrees
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.geom.Arc2D
import javax.swing.JComponent
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
import kotlin.math.cos
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class JLoadingSpinner : JComponent() {
    private val timer = Timer(10, ActionListener { e: ActionEvent? ->
        tick()
    })

    init {
        addAncestorListener(object : AncestorListener {
            override fun ancestorAdded(event: AncestorEvent?) {
                timer.start()
            }

            override fun ancestorRemoved(event: AncestorEvent?) {
                timer.stop()
            }

            override fun ancestorMoved(event: AncestorEvent?) {

            }
        })
        val size = UIManager.getFont("Panel.font").size
        preferredSize = Dimension(size, size)
    }

    private var leadingAngle = 0.degrees
    private var trailingAngle = 0.degrees
    private val startedAt = TimeSource.Monotonic.markNow()

    private fun ease(progress: Double): Double {
        return (((-cos(progress * Math.PI) + 1.0) / 2.0) + progress) / 2.0
    }
    private fun easeTrailing(progress: Double): Double {
        return if (progress < 0.5) {
            (1.0 - ease(progress)) * -MIN_ARC_FRACTION
        } else {
            ease((progress - 0.5) * 2.0) * (1 - MIN_ARC_FRACTION)
        }
    }
    private fun tick() {
        val progress = (startedAt.elapsedNow() / INTERVAL) % 1.0
        leadingAngle = 360.degrees * ease(progress)
        trailingAngle = 360.degrees * easeTrailing(progress)

        repaint()
    }

    private val padding = 2

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        g as Graphics2D

        val outerSize = getWidth().coerceAtMost(getHeight())
        val stroke = BasicStroke(outerSize.toFloat() * 0.07f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val size = (outerSize - padding * 2).toFloat() - stroke.lineWidth

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.stroke = stroke
        g.color = UIManager.getColor("Panel.foreground")

        val arc: Arc2D = Arc2D.Float(
            width.toFloat() / 2.0f - size / 2.0f,
            height.toFloat() / 2.0f - size / 2.0f,
            size,
            size,
            (90.degrees - trailingAngle).toFloatInDegrees(),
            -(leadingAngle - trailingAngle).toFloatInDegrees(),
            Arc2D.OPEN,
        )
        g.draw(arc)
    }

    companion object {
        val INTERVAL = 1.seconds
        val MIN_ARC_FRACTION = 0.1
    }
}