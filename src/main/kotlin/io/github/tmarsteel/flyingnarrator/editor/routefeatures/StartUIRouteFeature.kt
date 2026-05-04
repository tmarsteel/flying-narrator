package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.tmarsteel.flyingnarrator.editor.RouteEditorViewModel
import javax.imageio.ImageIO

class StartUIRouteFeature(
    viewModel: RouteEditorViewModel,
) : ImageUIRouteFeature(
    viewModel,
    ICON,
    viewModel.start,
) {
    override val zIndex: Int = 10

    companion object {
        val ICON by lazy {
            ImageIO.read(StartUIRouteFeature::class.java.getResource("start-flag-32.png")!!)
        }
    }
}