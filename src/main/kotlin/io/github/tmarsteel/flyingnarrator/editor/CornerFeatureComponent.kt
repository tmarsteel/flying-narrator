package io.github.tmarsteel.flyingnarrator.editor

import io.github.tmarsteel.flyingnarrator.feature.Feature
import io.github.tmarsteel.flyingnarrator.route.Route
import java.awt.Color

class CornerFeatureComponent(
    route: Route,
    val corner: Feature.Corner,
) : RouteStretchComponent(
    route,
    Unit.run {
        IntRange(route.indexOf(corner.segments.first()), route.indexOf(corner.segments.last()))
    },
    Color.ORANGE,
    Color(0x8020FF00.toInt(), true)
)