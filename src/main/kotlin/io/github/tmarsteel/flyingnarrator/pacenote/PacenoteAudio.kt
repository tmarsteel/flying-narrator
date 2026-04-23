package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.io.CompactObjectListSerializer
import io.github.tmarsteel.flyingnarrator.io.KotlinDurationAsMillisecondsSerializer
import io.github.tmarsteel.flyingnarrator.io.SystemPathSerializer
import io.github.tmarsteel.flyingnarrator.tts.SpeechSynthesizer
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLBreak
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLDocument
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLElement
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLMark
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Path
import java.util.Locale
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration

/**
 * Audio for all [PacenoteAtom]s in a route plus markings where each [PacenoteAtom] starts in the audio
 * data as well as the original [PacenoteAtom.physicalFeaturesAtDistanceAlongRoute].
 * This data is what you can save and share after curation/quality check. Further customization and adaption
 * (e.g. considering the drivers pace) then happens in real time with [CuedPacenoteAudio].
 */
@Serializable
data class PacenoteAudio(
    @Serializable(with = SystemPathSerializer::class)
    val audioFile: Path,

    @Serializable(with = CompactObjectListSerializer::class)
    val markers: List<CallData>,
) {
    fun withMappedAudioFilePath(mapper: (Path) -> Path): PacenoteAudio {
        return copy(audioFile = mapper(audioFile))
    }

    @Serializable
    data class CallData(
        /**
         * The duration within [PacenoteAudio.audioFile] at which the sound for this callout starts
         */
        @Serializable(with = KotlinDurationAsMillisecondsSerializer::class)
        val callAudioStartsAt: Duration,

        /**
         * The duration within [PacenoteAudio.audioFile] at which the sound for this callout ends
         */
        @Serializable(with = KotlinDurationAsMillisecondsSerializer::class)
        val callAudioEndsAt: Duration,

        /**
         * just copied from [PacenoteAtom]
         */
        val metadata: PacenoteAtom.Metadata,
    ) {
        val duration: Duration get() = callAudioEndsAt - callAudioStartsAt
        init {
            require(callAudioEndsAt > callAudioStartsAt)
        }
    }

    companion object {
        fun render(
            pacenotes: Iterable<PacenoteAtom>,
            synthesizer: SpeechSynthesizer,
            localePreference: List<Locale.LanguageRange> = listOf(Locale.LanguageRange("en-US")),
            storeAudioIn: Path = File.createTempFile("pacenote-audio", ".wav").run {
                deleteOnExit()
                toPath()
            }
        ): PacenoteAudio {
            val locale = pacenotes.first().selectLocale(localePreference)
            val ssmlMarkersByPacenoteAtom = mutableListOf<Triple<PacenoteAtom, String, String>>()
            val ssmlElements = mutableListOf<SSMLElement>()
            var previousEndedAt = "start"
            ssmlElements.add(SSMLMark(previousEndedAt))
            for ((index, atom) in pacenotes.withIndex()) {
                ssmlElements.add(atom.toSSML(locale))
                val endMarkerName = "mark$index"
                ssmlElements.add(SSMLMark(endMarkerName))
                ssmlMarkersByPacenoteAtom.add(Triple(atom, previousEndedAt, endMarkerName))
                previousEndedAt = endMarkerName
                ssmlElements.add(SSMLBreak(strength = SSMLBreak.Strength.MEDIUM)) // to prevent calls from flowing into each other
            }

            val synthesized = synthesizer.synthesize(SSMLDocument(locale, ssmlElements))
            AudioSystem.write(synthesized.openNewAudioInputStream(), AudioFileFormat.Type.WAVE, storeAudioIn.toFile())
            val callMarkers = ssmlMarkersByPacenoteAtom
                .map { (atom, startMark, endMark) ->
                    CallData(
                        synthesized.ssmlMakers.getValue(startMark),
                        synthesized.ssmlMakers.getValue(endMark),
                        atom.metadata,
                    )
                }

            return PacenoteAudio(storeAudioIn, callMarkers)
        }
    }
}