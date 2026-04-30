package io.github.tmarsteel.flyingnarrator.codriver

import io.github.tmarsteel.flyingnarrator.route.Speedmap
import io.github.tmarsteel.flyingnarrator.ui.LocalizedString
import io.github.tmarsteel.flyingnarrator.unit.Distance

/**
 * Information about the currently playing route needed for the codriver
 */
data class CodriverRouteInfo(
    /**
     * An identifier for the source/origin of the route, e.g. a game name like `dirt-rally-2`
     */
    val sourceId: String,

    /**
     * An identifier for the track unique to the [sourceId]
     */
    val trackId: String,

    /**
     * the name of the route which can be displayed
     */
    val displayName: LocalizedString,

    /**
     * Distance from start to finish line
     */
    val length: Distance,

    /**
     * A suitable speedmap for the route, ideally adapted to the capabilities of the car in use
     */
    val speedmap: Speedmap,
)