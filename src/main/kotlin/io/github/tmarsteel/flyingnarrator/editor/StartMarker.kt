package io.github.tmarsteel.flyingnarrator.editor

import javax.imageio.ImageIO

class StartMarker(
    viewModel: RouteEditorViewModel,
) : ImageSinglePointOnRouteComponent(
    ICON,
    viewModel,
    viewModel.mathSegments.first().startPoint,
    EditableSinglePointOnRouteComponent.EditGovernor.NotEditable,
) {
    companion object {
        val ICON by lazy {
            ImageIO.read(StartMarker::class.java.getResource("start-flag-32.png")!!)
        }
    }
}