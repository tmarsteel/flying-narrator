package io.github.tmarsteel.flyingnarrator.pacenote

/**
 * A [PacenoteAudio], but the cues are now concrete progress values (0-1), which can be directly utilized to play
 * back the callouts as the car progresses through the stage. The actual cues are also based on driver preference,
 * and ideally, consider the speed of the car (DiRT Rally 2 doesn't).
 */
class CuedPacenoteAudio(
    val baseAudio: PacenoteAudio,
) {
    data class CuedCallout(
        val triggerAt: Double,
        val callData: PacenoteAudio.CallData,
    )
}