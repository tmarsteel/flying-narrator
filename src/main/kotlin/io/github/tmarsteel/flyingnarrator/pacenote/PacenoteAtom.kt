package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLElement

/**
 * An _arbitrarily sized_ piece of information about the race route, which should be delivered to the driver
 * uninterrupted. The decision how to piecemeal the entire route description is in the domain logic of a
 * pacenote system and may vary as per driver preference.
 *
 * As such, this interface focuses only on converting the pacenote to human-understandable formats and cueing
 * information.
 */
interface PacenoteAtom {
    /**
     * The distance on the route where the physical elements described by this [PacenoteAtom] are located,
     * e.g. the entry of a corner for a [PacenoteAtom] describing a corner. This is used to cue the pacenote
     * item as much to the drivers preference as possible.a
     */
    val physicalFeaturesAtDistanceAlongRoute: Double

    /**
     * @return a representation of this [PacenoteAtom] suitable for text-to-speech synthesis.
     */
    fun toSSML(): SSMLElement
}