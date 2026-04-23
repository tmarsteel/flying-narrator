package io.github.tmarsteel.flyingnarrator.route

import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters

/**
 * Part of a rally race track / route. Right now holds only the direction and distance of the track,
 * but in the future may hold more info such as track width, surface, camber, ...
 */
class RoadSegment(
    val forward: Vector3,
) {
    val length: Distance get()= forward.length.meters // this approximation is likely good enough forever

    fun withForward(newForward: Vector3): RoadSegment {
        return RoadSegment(newForward)
    }
}

typealias Route = List<RoadSegment>