package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.signalOf
import io.github.tmarsteel.flyingnarrator.route.Route
import javax.imageio.ImageIO

class FinishComponent(
    route: Route,
) : LocationOnRouteComponent(signalOf(route.finish)) {
    init {
        setSize(ICON.width, ICON.height)
    }

    override fun paintComponent(g: java.awt.Graphics) {
        super.paintComponents(g)
        g.drawImage(ICON, 0, 0, null)
    }

    companion object {
        val ICON by lazy {
            ImageIO.read(FinishComponent::class.java.getResource("finish-flag-32.png")!!)
        }
    }
}