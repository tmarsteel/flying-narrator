package io.github.tmarsteel.flyingnarrator.pacenote

import io.github.tmarsteel.flyingnarrator.audio.concatenate
import io.github.tmarsteel.flyingnarrator.audio.opus.OggOpusEncoding
import io.github.tmarsteel.flyingnarrator.audio.timeLength
import io.github.tmarsteel.flyingnarrator.io.ByteArrayBase64Serializer
import io.github.tmarsteel.flyingnarrator.io.CompactObjectListSerializer
import io.github.tmarsteel.flyingnarrator.io.KotlinDurationAsMillisecondsSerializer
import io.github.tmarsteel.flyingnarrator.tts.SpeechSynthesisInputTooLongException
import io.github.tmarsteel.flyingnarrator.tts.SpeechSynthesizer
import io.github.tmarsteel.flyingnarrator.tts.SynthesizedSpeech
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLDocument
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLElement
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLMark
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration

/**
 * Audio for all [PacenoteAtom]s in a route plus markings where each [PacenoteAtom] starts in the audio
 * data as well as the original [PacenoteAtom.physicalFeaturesAtDistanceAlongRoute].
 * This data is what you can save and share after curation/quality check. Further customization and adaption
 * (e.g. considering the drivers pace) then happens in real time with [CuedAudioPacenotes].
 */
@Serializable
class AudioPacenotes(
    @Serializable(with = ByteArrayBase64Serializer::class)
    val encodedAudio: ByteArray,

    @Serializable(with = CompactObjectListSerializer::class)
    val markers: List<CallData>,
) {
    @Serializable
    data class CallData(
        /**
         * The duration within [AudioPacenotes.audioFile] at which the sound for this callout starts
         */
        @Serializable(with = KotlinDurationAsMillisecondsSerializer::class)
        val callAudioStartsAt: Duration,

        /**
         * The duration within [AudioPacenotes.audioFile] at which the sound for this callout ends
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
        fun render(
            pacenotes: List<PacenoteAtom>,
            synthesizer: SpeechSynthesizer,
            localePreference: List<Locale.LanguageRange> = listOf(Locale.LanguageRange("en-US")),
            audioEncoding: AudioFormat.Encoding = OPUS_ENCODING,
        ): AudioPacenotes {
            val parts = renderToMemory(pacenotes, synthesizer, localePreference)
            return merge(parts, audioEncoding)
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

        fun merge(
            parts: List<Pair<SynthesizedSpeech, List<CallData>>>,
            audioEncoding: AudioFormat.Encoding = OPUS_ENCODING,
        ): AudioPacenotes {
            val callsWithPartOffset = mutableListOf<Pair<Duration, List<CallData>>>()
            val audioIns = parts.map { it.first.openNewAudioInputStream() }
            val concatenatedAudio = audioIns.concatenate()
            var durationCarry = Duration.ZERO
            for ((idx, audioIn) in audioIns.withIndex()) {
                callsWithPartOffset.add(Pair(durationCarry, parts[idx].second))
                durationCarry += audioIn.timeLength
            }

            val outBuffer = ByteArrayOutputStream()
            AudioSystem.getAudioInputStream(audioEncoding, concatenatedAudio).use { encodedAudioIn ->
                encodedAudioIn.transferTo(outBuffer)
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

            return AudioPacenotes(
                outBuffer.toByteArray(),
                adjustedCalls,
            )
        }

        val OPUS_ENCODING = OggOpusEncoding(
            application = OggOpusEncoding.Application.AUDIO,
            signal = OggOpusEncoding.Signal.VOICE,
            complexity = 10,
            bitsPerSecond = 48_000, // voice doesn't need more than this
        )
    }
}