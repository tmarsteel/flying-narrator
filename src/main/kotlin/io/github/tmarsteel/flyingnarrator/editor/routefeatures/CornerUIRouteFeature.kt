package io.github.tmarsteel.flyingnarrator.editor.routefeatures

import io.github.fenrur.signal.operators.map
import io.github.tmarsteel.flyingnarrator.editor.RouteEditorViewModel
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sum
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sumOf
import java.awt.Color
import javax.swing.JToolTip
import javax.swing.UIManager

class CornerUIRouteFeature(
    routeModel: RouteEditorViewModel,
    stretchModel: RouteEditorViewModel.CornerModel,
) : StretchUIRouteFeature(
    routeModel,
    stretchModel,
    UIManager.getColor(KEY_DISPLAY_COLOR)
        ?: UIManager.getColor("Component.accentColor")
        ?: Color.ORANGE,
    UIManager.getColor(KEY_HOVER_COLOR)
        ?: Color(0x20FF00),
    true,
) {
    private val cornerSegments = stretchModel.segmentIndices.map { idxs ->
        routeViewModel.segments.slice(idxs)
    }

    init {
        cornerSegments.subscribeOn(lifecycle) { segments ->
            val totalAngle = segments.asSequence()
                .windowed(size = 2, step = 1, partialWindows = false)
                .map { (a, b) -> a.base.forward.angleTo(b.base.forward) }
                .sum()
            val text = StringBuilder()
            text.append("<html>")
            text.append("Ør=")
            text.append(segments.map { it.base }.compoundRadius)
            text.append("<br>")
            text.append("∠=")
            text.append(totalAngle)
            text.append("<br>")
            text.append("d=")
            text.append(segments.sumOf { it.base.length }.toString())
            text.append("m<br>")
            text.append("@")
            text.append("??")
            text.append("</html>")

            tooltip.tipText = text.toString()
            tooltip.size = tooltip.preferredSize
        }
    }

    override val tooltip = run {
        JToolTip().apply {
            isOpaque = true
            isVisible = true
            tipText = ""
        }
    }

    companion object {
        val KEY_DISPLAY_COLOR = "${CornerUIRouteFeature::class.simpleName}.displayColor"
        val KEY_HOVER_COLOR = "${CornerUIRouteFeature::class.simpleName}.hoverColor"
    }
}