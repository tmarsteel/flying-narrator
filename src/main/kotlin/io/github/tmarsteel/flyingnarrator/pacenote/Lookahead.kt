package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.route.Speedmap
import io.github.tmarsteel.flyingnarrator.unit.Distance

/**
 * Models the drivers preference of when to receive callouts.
 */
interface Lookahead {
    /**
     * Given a [callout] describing a physical feature of the route, determines the distance along the track
     * at which the callout must have finished playing back _latest_.
     */
    fun determineCalloutLocation(
        callout: AudioPacenotes.CallData,
        speedmap: Speedmap,
    ): Distance

    companion object {
        fun ofConstantDistance(distance: Distance): Lookahead = object : Lookahead {
            override fun determineCalloutLocation(
                callout: AudioPacenotes.CallData,
                speedmap: Speedmap
            ): Distance {
                return callout.metadata.physicalFeaturesAtDistanceAlongRoute - distance
            }
        }
    }
}