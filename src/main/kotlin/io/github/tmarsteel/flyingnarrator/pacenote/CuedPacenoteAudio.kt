package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.Speedmap
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import java.util.Collections
import java.util.LinkedList
import java.util.TreeMap

/**
 * A [PacenoteAudio], but the cues are now concrete progress values (0-1), which can be directly utilized to play
 * back the callouts as the car progresses through the stage. The actual cues are also based on driver preference,
 * and ideally, consider the speed of the car (DiRT Rally 2 doesn't).
 */
class CuedPacenoteAudio private constructor(
    val baseAudio: PacenoteAudio,
    val cues: TreeMap<Distance, CuedCallout>,
) {
    constructor(baseAudio: PacenoteAudio, cues: List<CuedCallout>) : this(
        baseAudio,
        TreeMap(cues.associateBy { it.triggerAtDistanceAlongRoute })
    )

    /**
     * **For this function to work properly, any invocation as [fromPositionExclusive] an exactly equal value as to what
     * was passed to [toPositionInclusive] in the invocation directly prior.** Otherwise, some triggers may be missed.
     * This function is thread-safe.
     *
     * @param fromPositionExclusive the previously investigated position on the track, or `0m` at the start of the race
     * @param toPositionInclusive the current position on track
     * @return the callouts that have been triggered while the car traversed the route between [fromPositionExclusive]
     * and [toPositionInclusive], in ascending order of trigger location/time
     */
    fun findTriggeredCues(fromPositionExclusive: Distance, toPositionInclusive: Distance): Iterable<CuedCallout> {
        return cues.subMap(fromPositionExclusive, false, toPositionInclusive, true)
            .values
            .let(Collections::unmodifiableCollection)
    }

    interface CuedCallout {
        /**
         * The distance along the track at which to trigger this callout
         */
        val triggerAtDistanceAlongRoute: Distance

        val callData: PacenoteAudio.CallData
    }

    private class CuedCalloutImpl(
        /**
         * The distance along the track at which to trigger this callout
         */
        override val triggerAtDistanceAlongRoute: Distance,

        /**
         * The distance along the track that will probably be reached by the time the callout finishes
         */
        val finishesAtDistanceAlongRoute: Distance,

        override val callData: PacenoteAudio.CallData
    ) : CuedCallout

    companion object {
        /**
         * Default cueing algorithm, but more details/vartiety/customizability might be needed.
         *
         * @param pacenotes the pacenotes to play back; must start at the start line and may extend beyond the finish
         *                  line
         * @param raceLength length of the racing part of the route (start line to finish line)
         * @param speedmap used to adapt the timing of the callouts to the speed of the car
         */
        fun cueue(
            pacenotes: PacenoteAudio,
            raceLength: Distance,
            speedmap: Speedmap,
            lookahead: Lookahead,
        ): CuedPacenoteAudio {
            val finishLineCallout = pacenotes.markers.find { it.metadata.finishLineAtOffset >= 0.meters }
            val lastImportantPoint = finishLineCallout
                ?.metadata?.run { physicalFeaturesAtDistanceAlongRoute + finishLineAtOffset }
                ?: (raceLength + 100.meters)
            val indexOfLastImportantCallout = pacenotes.markers.indexOfFirst {
                it == finishLineCallout || it.metadata.physicalFeaturesAtDistanceAlongRoute > lastImportantPoint
            }

            val cues = LinkedList<CuedCalloutImpl>()

            cueCalloutsAvoidingOverlapBackwards(
                pacenotes.markers.subList(0, indexOfLastImportantCallout + 1),
                cues,
                speedmap,
                lookahead,
            )

            cueCalloutsAvoidingOverlapForward(
                pacenotes.markers.subList(indexOfLastImportantCallout + 1, pacenotes.markers.size),
                cues,
                speedmap,
                lookahead,
            )

            return CuedPacenoteAudio(pacenotes, cues)
        }

        private fun cueCalloutsAvoidingOverlapBackwards(
            calls: List<PacenoteAudio.CallData>,
            cuesOut: MutableList<CuedCalloutImpl>,
            speedmap: Speedmap,
            lookahead: Lookahead,
        ) {
            for (call in calls.asReversed()) {
                val desiredCalloutEndAt = lookahead.determineCalloutLocation(call, speedmap)
                check(desiredCalloutEndAt >= call.metadata.physicalFeaturesAtDistanceAlongRoute) {
                    "Negative Lookahead (\"look behind\") on callout $call; occurs at ${call.metadata.physicalFeaturesAtDistanceAlongRoute} but should be voiced by $desiredCalloutEndAt"
                }
                val nextCalloutStartsAt = cuesOut.firstOrNull()?.triggerAtDistanceAlongRoute ?: Double.POSITIVE_INFINITY.meters
                // TODO: customize handling when callouts overlap?
                // e.g. one could go back to the speech synthesizer and render a faster-spoken version of the callout
                val calloutActualEndAt = desiredCalloutEndAt.coerceAtMost(nextCalloutStartsAt)
                val calloutEndTime = speedmap.estimateDurationUntilDistance(calloutActualEndAt)
                val calloutStartTime = calloutEndTime - call.duration
                val calloutStartAt = speedmap.estimatePositionAtTime(calloutStartTime)

                cuesOut.addFirst(CuedCalloutImpl(
                    calloutStartAt,
                    calloutActualEndAt,
                    call,
                ))
            }
        }

        private fun cueCalloutsAvoidingOverlapForward(
            calls: List<PacenoteAudio.CallData>,
            cuesOut: MutableList<CuedCalloutImpl>,
            speedmap: Speedmap,
            lookahead: Lookahead,
        ) {
            for (calloutAfterFinish in calls) {
                val previousCalloutEndedAt = cuesOut.lastOrNull()?.triggerAtDistanceAlongRoute ?: 0.meters
                val triggerAt = lookahead.determineCalloutLocation(calloutAfterFinish, speedmap).coerceAtLeast(previousCalloutEndedAt)
                val triggerAtTime = speedmap.estimateDurationUntilDistance(triggerAt)
                val endsAt = speedmap.estimatePositionAtTime(triggerAtTime + calloutAfterFinish.duration)

                cuesOut.addLast(CuedCalloutImpl(
                    triggerAt,
                    endsAt,
                    calloutAfterFinish,
                ))
            }
        }
    }
}