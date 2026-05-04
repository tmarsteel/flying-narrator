package io.github.tmarsteel.flyingnarrator.editor

import javax.imageio.ImageIO

class StartMarker(
    viewModel: RouteEditorViewModel,
) : ImageSinglePointOnRouteComponent(
    viewModel,
    ICON,
    viewModel.start,
) {
    override val editGovernor = PointOnTrackEditHandle.EditGovernor.NotEditable
    override val zIndex: Int = 10

    companion object {
        val ICON by lazy {
            ImageIO.read(StartMarker::class.java.getResource("start-flag-32.png")!!)
        }
    }
}