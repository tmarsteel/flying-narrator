package io.github.tmarsteel.flyingnarrator.editor

import javax.imageio.ImageIO

class FinishMarker(
    viewModel: RouteEditorViewModel,
) : ImageSinglePointOnRouteComponent(
    ICON,
    viewModel,
    viewModel.segments.last().line.endPoint,
    EditableSinglePointOnRouteComponent.EditGovernor.NotEditable,
) {
    companion object {
        val ICON by lazy {
            ImageIO.read(FinishMarker::class.java.getResource("finish-flag-32.png")!!)
        }
    }
}