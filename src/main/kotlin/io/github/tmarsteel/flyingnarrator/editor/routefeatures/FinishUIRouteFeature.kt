package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.tmarsteel.flyingnarrator.editor.RouteEditorViewModel
import javax.imageio.ImageIO

class FinishUIRouteFeature(
    viewModel: RouteEditorViewModel,
) : ImageUIRouteFeature(
    viewModel,
    ICON,
    viewModel.finish,
) {
    override val zIndex: Int = 10

    companion object {
        val ICON by lazy {
            ImageIO.read(FinishUIRouteFeature::class.java.getResource("finish-flag-32.png")!!)
        }
    }
}