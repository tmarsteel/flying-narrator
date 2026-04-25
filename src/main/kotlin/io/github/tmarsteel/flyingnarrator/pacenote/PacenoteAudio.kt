package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.audio.concatenate
import io.github.tmarsteel.flyingnarrator.audio.timeLength
import io.github.tmarsteel.flyingnarrator.io.CompactObjectListSerializer
import io.github.tmarsteel.flyingnarrator.io.KotlinDurationAsMillisecondsSerializer
import io.github.tmarsteel.flyingnarrator.io.SystemPathSerializer
import io.github.tmarsteel.flyingnarrator.tts.SpeechSynthesisInputTooLongException
import io.github.tmarsteel.flyingnarrator.tts.SpeechSynthesizer
import io.github.tmarsteel.flyingnarrator.tts.SynthesizedSpeech
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLDocument
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLElement
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLMark
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Path
import java.util.Locale
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioSystem
import kotlin.io.path.outputStream
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
            require(callAudioEndsAt >= callAudioStartsAt) {
                "Callout audio with negative length?? Starts at $callAudioStartsAt, ends at $callAudioEndsAt"
            }
        }
    }

    companion object {
        fun renderToFile(
            pacenotes: List<PacenoteAtom>,
            synthesizer: SpeechSynthesizer,
            localePreference: List<Locale.LanguageRange> = listOf(Locale.LanguageRange("en-US")),
            fileType: AudioFileFormat.Type = AudioFileFormat.Type.WAVE,
            storeAudioIn: Path = File.createTempFile("pacenote-audio", "." + fileType.extension).run {
                deleteOnExit()
                toPath()
            },
        ): PacenoteAudio {
            val parts = renderToMemory(pacenotes, synthesizer, localePreference)
            return mergeToFile(parts, storeAudioIn, fileType)
        }

        fun renderToMemory(
            pacenotes: List<PacenoteAtom>,
            synthesizer: SpeechSynthesizer,
            localePreference: List<Locale.LanguageRange> = listOf(Locale.LanguageRange("en-US")),
        ): List<Pair<SynthesizedSpeech, List<CallData>>> {
            val locale = pacenotes.first().selectLocale(localePreference)
            val ssmlMarkersByPacenoteAtom = mutableListOf<Triple<PacenoteAtom, String, String>>()
            val ssmlElements = mutableListOf<SSMLElement>()
            var previousEndedAt = "start"
            ssmlElements.add(SSMLMark(previousEndedAt))
            for ((index, atom) in pacenotes.withIndex()) {
                val atomAsSSML = atom.toSSML(locale)
                if (atomAsSSML.isEmpty) {
                    continue
                }

                ssmlElements.add(atomAsSSML)
                val endMarkerName = "mark$index"
                ssmlElements.add(SSMLMark(endMarkerName))
                ssmlMarkersByPacenoteAtom.add(Triple(atom, previousEndedAt, endMarkerName))
                previousEndedAt = endMarkerName
                //ssmlElements.add(SSMLBreak(strength = SSMLBreak.Strength.WEAK)) // to prevent calls from flowing into each other
            }

            val synthesized = try {
                synthesizer.synthesize(SSMLDocument(locale, ssmlElements))
            } catch (ex: SpeechSynthesisInputTooLongException) {
                if (pacenotes.size < 2) {
                    throw SpeechSynthesisInputTooLongException("Input is too long, and even subdividing into single atoms doesn't shorten enough.", ex)
                }
                val partA = renderToMemory(
                    pacenotes.subList(0, pacenotes.size / 2),
                    synthesizer,
                    localePreference,
                )
                val partB = renderToMemory(
                    pacenotes.subList(pacenotes.size / 2, pacenotes.size),
                    synthesizer,
                    localePreference,
                )
                return partA + partB
            }

            val callMarkers = ssmlMarkersByPacenoteAtom
                .map { (atom, startMark, endMark) ->
                    CallData(
                        synthesized.ssmlMakers.getValue(startMark),
                        synthesized.ssmlMakers.getValue(endMark),
                        atom.metadata,
                    )
                }

            return listOf(Pair(synthesized, callMarkers))
        }

        fun mergeToFile(
            parts: List<Pair<SynthesizedSpeech, List<CallData>>>,
            storeAudioIn: Path,
            fileType: AudioFileFormat.Type,
        ): PacenoteAudio {
            val callsWithPartOffset = mutableListOf<Pair<Duration, List<CallData>>>()
            storeAudioIn.outputStream().use { fileOut ->
                val audioIns = parts.map { it.first.openNewAudioInputStream() }
                val concatenatedAudio = audioIns.concatenate()
                var durationCarry = Duration.ZERO
                for ((idx, audioIn) in audioIns.withIndex()) {
                    callsWithPartOffset.add(Pair(durationCarry, parts[idx].second))
                    durationCarry += audioIn.timeLength
                }

                AudioSystem.write(concatenatedAudio, fileType, fileOut)
            }

            val adjustedCalls = mutableListOf<CallData>()
            for ((offset, calls) in callsWithPartOffset) {
                if (offset == Duration.ZERO) {
                    adjustedCalls += calls
                    continue
                }

                for (call in calls) {
                    adjustedCalls += call.copy(
                        callAudioStartsAt = offset + call.callAudioStartsAt,
                        callAudioEndsAt = offset + call.callAudioEndsAt,
                    )
                }
            }

            return PacenoteAudio(storeAudioIn, adjustedCalls)
        }
    }
}