package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.feature.compoundRadius
import java.awt.Color
import javax.swing.JToolTip
import kotlin.math.roundToInt

class CornerFeatureComponent(
    viewModel: RouteEditorViewModel,
    val corner: Feature.Corner,
) : RouteStretchComponent(
    viewModel,
    Unit.run {
        IntRange(viewModel.route.indexOf(corner.segments.first()), viewModel.route.indexOf(corner.segments.last()))
    },
    Color.ORANGE,
    Color(0x8020FF00.toInt(), true),
    true,
) {
    override val tooltip = run {
        val text = StringBuilder()
        text.append("<html>")
        text.append("Ør=")
        text.append(corner.segments.compoundRadius)
        text.append("<br>")
        text.append("∠=")
        text.append(corner.totalAngle)
        text.append("<br>")
        text.append("d=")
        text.append(corner.length.toDoubleInMeters().roundToInt())
        text.append("m<br>")
        text.append("@")
        text.append(corner.startsAtTrackDistance.toString())
        text.append("</html>")

        JToolTip().apply {
            isOpaque = true
            isVisible = true
            tipText = text.toString()
            size = preferredSize
        }
    }
}