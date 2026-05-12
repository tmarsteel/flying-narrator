package io.github.tmarsteel.flyingnarrator.ui

import com.formdev.flatlaf.ui.FlatUIUtils
import com.formdev.flatlaf.util.UIScale
import io.github.tmarsteel.flyingnarrator.unit.Distance
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import kotlin.math.ceil
import kotlin.math.floor

class StraightPacenoteIcon private constructor(val meters: Int) : Icon {
    val distanceText = meters.toString()
    private val bounds by lazy {
        val scale = UIScale.scale(1f).toDouble()
        val frc = FontRenderContext(AffineTransform.getScaleInstance(scale, scale), true, true)
        font.getStringBounds(distanceText, frc)
    }

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        g.font = font
        g.color = c.foreground
        FlatUIUtils.setRenderingHints(g)
        g.drawString(distanceText, x, y + bounds.height.toInt())
    }

    override fun getIconWidth(): Int {
        return ceil(bounds.width).toInt()
    }

    override fun getIconHeight(): Int {
        return ceil(bounds.height).toInt()
    }

    companion object {
        private val font by lazy {
            Font.createFont(Font.TRUETYPE_FONT, StraightPacenoteIcon::class.java.getResourceAsStream("ShadowsIntoLightTwo-Regular.ttf"))
                .deriveFont(Font.PLAIN, 18f)
        }

        private val cache = ConcurrentHashMap<Int, StraightPacenoteIcon>()

        @JvmStatic
        @JvmName("of")
        operator fun invoke(distance: Distance): StraightPacenoteIcon {
            val meters = floor(distance.toDoubleInMeters()).toInt()
            return cache.computeIfAbsent(meters, ::StraightPacenoteIcon)
        }
    }
}