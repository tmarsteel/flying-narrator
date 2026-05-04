package io.github.tmarsteel.flyingnarrator.editor

import io.github.fenrur.signal.operators.map
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import io.github.tmarsteel.flyingnarrator.ui.reactive.subscribeOn
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sum
import io.github.tmarsteel.flyingnarrator.unit.ScalarLike.Companion.sumOf
import java.awt.Color
import javax.swing.JToolTip

class CornerFeatureComponent(
    routeModel: RouteEditorViewModel,
    stretchModel: RouteEditorViewModel.Corner,
) : RouteStretchComponent(
    routeModel,
    stretchModel,
    Color.ORANGE,
    Color(0x8020FF00.toInt(), true),
    true,
) {
    private val cornerSegments = stretchModel.segmentIndices.map { idxs ->
        routeViewModel.route.subList(idxs.first, idxs.last + 1)
    }

    init {
        cornerSegments.subscribeOn(lifecycle) { segments ->
            val totalAngle = segments.asSequence()
                .windowed(size = 2, step = 1, partialWindows = false)
                .map { (a, b) -> a.forward.angleTo(b.forward) }
                .sum()
            val text = StringBuilder()
            text.append("<html>")
            text.append("Ør=")
            text.append(segments.compoundRadius)
            text.append("<br>")
            text.append("∠=")
            text.append(totalAngle)
            text.append("<br>")
            text.append("d=")
            text.append(segments.sumOf { it.length }.toString())
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
}