package io.github.tmarsteel.flyingnarrator.tts.gcloud

import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverData
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverDataSubcall
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLDocument
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLElement
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLSentence
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLText
import io.github.tmarsteel.flyingnarrator.tts.ssml.ssmlToString
import okhttp3.OkHttpClient
import tools.jackson.databind.MapperFeature
import tools.jackson.dataformat.xml.XmlFactory
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.module.jaxb.JaxbAnnotationModule
import tools.jackson.module.kotlin.kotlinModule
import java.nio.file.Paths
import java.util.Locale
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioSystem

fun main(args: Array<String>) {
    val objectMapper: XmlMapper = XmlMapper.Builder(XmlFactory())
        .addModule(kotlinModule())
        .addModule(JaxbAnnotationModule())
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .build()

    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-goog-api-key", System.getenv("GOOGLE_API_KEY"))
                .build()
            chain.proceed(request)
        }
        .build()

    val file = Paths.get(args[0])
    val calls = objectMapper.readValue(file.toFile(), DR2CodriverData::class.java).toSSML().take(20).toList()

    val synthesizer = GoogleCloudSpeechSynthesizer(httpClient = client)
    val ssml = SSMLDocument(
        Locale.ENGLISH,
        calls,
    )
    println(ssmlToString(ssml, pretty = true))

    val audio = synthesizer.synthesize(ssml)

    AudioSystem.write(audio, AudioFileFormat.Type.WAVE, Paths.get("out.wav").toFile())

}

private fun DR2CodriverData.toSSML(): Sequence<SSMLElement> {
    return codriverCalls.asSequence().map { call ->
        SSMLSentence(call.subcalls.flatMap { subCall ->
            subCall.toSSML() + sequenceOf(SSMLText(" "))
        })
    }
}

private fun DR2CodriverDataSubcall.toSSML(): Sequence<SSMLElement> {
    val prefixModifier: Sequence<SSMLElement> = if (DR2CodriverDataSubcall.Modifier.CAUTION  in listOf(modifierA, modifierB)) {
        sequenceOf(SSMLText("caution "))
    } else emptySequence()

    val postfixModifier: Sequence<SSMLElement> = if (DR2CodriverDataSubcall.Modifier.DONT_CUT  in listOf(modifierA, modifierB)) {
        sequenceOf(SSMLText(" don't cut"))
    } else emptySequence()

    val mainCall: Sequence<SSMLElement> = when (this.type) {
        DR2CodriverDataSubcall.Type.EMPTY,
        DR2CodriverDataSubcall.Type.STRAIGHT -> emptySequence()
        DR2CodriverDataSubcall.Type.LEFT_TURN,
        DR2CodriverDataSubcall.Type.RIGHT_TURN -> {
            val severity = enumValues<DR2CodriverDataSubcall.Severity>()
                .filter { it.maxAngle >= this.angle }
                .min()
            val direction = if (this.type == DR2CodriverDataSubcall.Type.LEFT_TURN) {
                "left"
            } else {
                "right"
            }
            sequenceOf(SSMLText("${severity.name.lowercase()} $direction"))
        }
        DR2CodriverDataSubcall.Type.FINISH -> sequenceOf(SSMLText("Finish"))
        DR2CodriverDataSubcall.Type.DIP -> sequenceOf(SSMLText("through dip"))
        DR2CodriverDataSubcall.Type.BUMP_OR_CREST_OR_JUMP -> sequenceOf(SSMLText("over crest"))
        else -> error("unknown subcall type: $type")
    }

    val distanceLink: Sequence<SSMLElement> = when {
        distanceLink.distanceInMeters != null -> sequenceOf(SSMLText(" ${distanceLink.distanceInMeters}"))
        else -> when (distanceLink) {
            DR2CodriverDataSubcall.DistanceLink.NONE -> emptySequence()
            DR2CodriverDataSubcall.DistanceLink.INTO -> sequenceOf(SSMLText("  into "))
            DR2CodriverDataSubcall.DistanceLink.PLUS -> sequenceOf(SSMLText("  and "))
            DR2CodriverDataSubcall.DistanceLink.TIGHTENS -> sequenceOf(SSMLText(" tightens "))
            DR2CodriverDataSubcall.DistanceLink.OPENS -> sequenceOf(SSMLText(" opens "))
            else -> error("unknown distance link: $distanceLink")
        }
    }

    return prefixModifier + mainCall + postfixModifier + distanceLink
}