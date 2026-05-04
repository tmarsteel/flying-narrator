package io.github.tmarsteel.flyingnarrator.editor

import javax.imageio.ImageIO

class FinishMarker(
    viewModel: RouteEditorViewModel,
) : ImageSinglePointOnRouteComponent(
    viewModel,
    ICON,
    viewModel.finish,
) {
    override val editGovernor = PointOnTrackEditHandle.EditGovernor.NotEditable
    override val zIndex: Int = 10

    companion object {
        val ICON by lazy {
            ImageIO.read(FinishMarker::class.java.getResource("finish-flag-32.png")!!)
        }
    }
}