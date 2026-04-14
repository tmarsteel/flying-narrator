package io.github.tmarsteel.flyingnarrator

/**
 * Part of a rally race track / route. Right now holds only the direction and distance of the track,
 * but in the future may hold more info such as track width, surface, camber, ...
 */
class RoadSegment(
    val forward: Vector3,
) {
    val length: Double get()= forward.length // this approximation is likely good enough forever

    fun withForward(newForward: Vector3): RoadSegment {
        return RoadSegment(newForward)
    }
}

typealias Route = List<RoadSegment>