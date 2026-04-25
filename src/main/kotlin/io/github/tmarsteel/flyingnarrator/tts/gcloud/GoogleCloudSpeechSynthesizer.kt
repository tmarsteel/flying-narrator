@file:UseSerializers(
    LocaleAsLanguageTagSerializer::class,
    ByteArrayBase64Serializer::class,
)
package io.github.tmarsteel.flyingnarrator.tts.gcloud

import io.github.tmarsteel.flyingnarrator.io.ByteArrayBase64Serializer
import io.github.tmarsteel.flyingnarrator.tts.InMemorySynthesizedSpeech
import io.github.tmarsteel.flyingnarrator.tts.SpeechSynthesisException
import io.github.tmarsteel.flyingnarrator.tts.SpeechSynthesisInputTooLongException
import io.github.tmarsteel.flyingnarrator.tts.SpeechSynthesizer
import io.github.tmarsteel.flyingnarrator.tts.SynthesizedSpeech
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLDocument
import io.github.tmarsteel.flyingnarrator.tts.ssml.ssmlToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class GoogleCloudSpeechSynthesizer(
    val baseUrl: HttpUrl = "https://texttospeech.googleapis.com/".toHttpUrl(),
    val httpClient: OkHttpClient = OkHttpClient(),
) : SpeechSynthesizer {
    private val synthesizeUrl = baseUrl.newBuilder().addPathSegments("v1beta1/text:synthesize").build()
    private val listVoicesUrl = baseUrl.newBuilder().addPathSegments("v1beta1/voices").build()

    override fun synthesize(document: SSMLDocument): SynthesizedSpeech {
        val ssmlString = ssmlToString(document)
        if (ssmlString.length > 5000) {
            throw SpeechSynthesisInputTooLongException("Google Cloud Speech API only supports SSML documents up to 5000 characters long")
        }
        val requestDto = TextSynthesisRequestDto(
            SynthesisInputDto(
                ssml = ssmlString,
            ),
            VoiceSelectionParamsDto(
                locale = document.lang,
            ),
            AudioConfigDto(
                audioEncoding = AudioConfigDto.AudioEncoding.OGG_OPUS,
            ),
            TimePointingMode.SSML_MARK,
        )
        val request = Request.Builder()
            .url(synthesizeUrl)
            .post(
                jsonFormat.encodeToString(requestDto)
                    .toRequestBody(jsonMediaType)
            )
            .build()

        val responseDto = execute<SynthesizeTextResponseDto>(request)
        return InMemorySynthesizedSpeech(
            responseDto.audioContent,
            responseDto.timepoints?.associate {
                it.markName to it.timeSeconds.seconds
            } ?: emptyMap()
        )
    }

    fun listVoices(forLocale: Locale? = null): List<VoiceDto> {
        val url = if (forLocale == null) {
            listVoicesUrl
        } else {
            listVoicesUrl.newBuilder()
                .addQueryParameter("languageCode", forLocale.toLanguageTag())
                .build()
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return execute<ListVoicesResponseDto>(request).voices
    }

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified R> execute(request: Request): R {
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = jsonFormat
                    .decodeFromStream<GoogleCloudErrorResponseDto>(response.body!!.byteStream())
                    .error
                throw SpeechSynthesisException(
                    error.message,
                    GoogleCloudAPIException(error),
                )
            }
            jsonFormat.decodeFromStream<R>(response.body!!.byteStream())
        }
    }

    companion object {
        private val jsonFormat = Json {
            explicitNulls = false
            ignoreUnknownKeys = true
        }
        private val jsonMediaType = "application/json;encoding=utf-8".toMediaType()
    }
}

@Serializable
private data class TextSynthesisRequestDto(
    val input: SynthesisInputDto,
    @SerialName("voice")
    val voiceSelectionParams: VoiceSelectionParamsDto,
    val audioConfig: AudioConfigDto,
    @SerialName("enableTimePointing")
    val timePointingMode: TimePointingMode,
)

@Serializable
private enum class TimePointingMode {
    TIMEPOINT_TYPE_UNSPECIFIED,
    SSML_MARK,
    ;
}

@Serializable
private data class SynthesisInputDto(
    val ssml: String? = null,
    val markup: String? = null,
    val prompt: String? = null,
)

@Serializable
private data class VoiceSelectionParamsDto(
    @SerialName("languageCode")
    val locale: Locale,
    val name: String? = null,
    val ssmlGender: SSMLGender = SSMLGender.SSML_VOICE_GENDER_UNSPECIFIED,
    val modelName: String? = null,
)

enum class SSMLGender {
    SSML_VOICE_GENDER_UNSPECIFIED,
    MALE,
    FEMALE,
    NEUTRAL,
    ;
}

@Serializable
private data class AudioConfigDto(
    val audioEncoding: AudioEncoding,
    val volumeGainDb: Double = 1.0,

) {
    enum class AudioEncoding {
        LINEAR16,
        MP3,
        OGG_OPUS,
        MULAW,
        ALAW,
        PCM,
        M4A,
        ;
    }
}

@Serializable
private data class Timepoint(
    val markName: String,
    val timeSeconds: Double,
)

@Serializable
private class SynthesizeTextResponseDto(
    val audioContent: ByteArray,
    val timepoints: List<Timepoint>? = null,
)

@Serializable
private data class ListVoicesRequestDto(
    @SerialName("languageCode")
    val locale: Locale? = null,
)

@Serializable
private data class ListVoicesResponseDto(
    val voices: List<VoiceDto>
)

@Serializable
data class VoiceDto(
    @SerialName("languageCodes")
    val supportedLocales: List<Locale>,

    val name: String,
    val ssmlGender: SSMLGender,
    val naturalSampleRateHertz: Int,
)