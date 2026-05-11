package io.github.tmarsteel.flyingnarrator.route

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.io.CompactObjectListSerializer
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import kotlinx.serialization.Serializable

/**
 * A minimalistic representation of a stage/racetrack that is optimized for serialization and allows for no
 * internal inconsistencies.
 */
@Serializable
data class RouteDto(
    /**
     * The segments that comprise the route, where the first segment starts at the start line
     */
    @Serializable(with = CompactObjectListSerializer::class)
    val segments: List<RouteSegmentDto>,

    /**
     * location of the finish line along the [segments]
     */
    val distanceToFinish: Distance,
)

@Serializable
data class RouteSegmentDto(
    /**
     * Points to the next point on the route, length in meters (see [Distance.meters]).
     */
    val forward: Vector3,
)