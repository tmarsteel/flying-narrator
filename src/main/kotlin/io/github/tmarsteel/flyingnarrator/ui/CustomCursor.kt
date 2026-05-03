package io.github.tmarsteel.flyingnarrator.ui

import java.awt.Point
import java.awt.Toolkit
import javax.imageio.ImageIO

object CustomCursor {
    private val toolkit = Toolkit.getDefaultToolkit()
    val GRABBING by lazy {
        toolkit.createCustomCursor(
            ImageIO.read(javaClass.getResourceAsStream("grabbing-cursor-32.png")),
            Point(4, 6),
            "grabbing"
        )
    }
}