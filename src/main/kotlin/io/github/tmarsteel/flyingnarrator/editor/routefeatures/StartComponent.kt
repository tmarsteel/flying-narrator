package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.tmarsteel.flyingnarrator.editor.RouteViewModel
import java.awt.Graphics
import javax.imageio.ImageIO

class StartComponent(
    viewModel: RouteViewModel,
) : LocationOnRouteComponent(viewModel.start) {
    init {
        setSize(ICON.width, ICON.height)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponents(g)
        g.drawImage(ICON, 0, 0, null)
    }

    companion object {
        val ICON by lazy {
            ImageIO.read(StartComponent::class.java.getResource("start-flag-32.png")!!)
        }
    }
}