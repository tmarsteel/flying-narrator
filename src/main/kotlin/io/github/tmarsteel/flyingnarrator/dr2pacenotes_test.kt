package io.github.tmarsteel.flyingnarrator

import io.github.tmarsteel.flyingnarrator.dirtrally2.DirtRally2PacenoteAtomAdapter
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2CodriverData
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2XMLMapper
import io.github.tmarsteel.flyingnarrator.io.FlyingNarratorJsonFormat
import io.github.tmarsteel.flyingnarrator.nefs.NefsCoordinates
import io.github.tmarsteel.flyingnarrator.nefs.NefsFile
import io.github.tmarsteel.flyingnarrator.nefs.protocol.Command
import io.github.tmarsteel.flyingnarrator.pacenote.PacenoteAudio
import io.github.tmarsteel.flyingnarrator.tts.gcloud.GoogleCloudSpeechSynthesizer
import io.github.tmarsteel.flyingnarrator.tts.ssml.SSMLDocument
import io.github.tmarsteel.flyingnarrator.tts.ssml.ssmlToString
import okhttp3.OkHttpClient
import tools.jackson.databind.util.ByteBufferBackedInputStream
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    val codriver_file_regex = Regex("(?<location>\\w+)_rally_(?<locationNumber>\\d+)_codriver_\\d+_pro_\\d+_data.xml")
    val codriverXml = NefsFile.open(NefsCoordinates.FileOnSystemDisk(Paths.get(args[0]))).use { routePackFile ->
        val codriverDataFile = routePackFile.listFiles(true)
            .asSequence()
            .filter { !it.isDirectory }
            .filter { it.fullPath.startsWith(args[1]) }
            .filter { it.fileName.matches(codriver_file_regex) }
            .single()

        routePackFile.readFile(codriverDataFile.id, Command.Conversion.UNPACK_BINARY_XML)
    }

    val codriverData = DR2XMLMapper.readValue(ByteBufferBackedInputStream(codriverXml), DR2CodriverData::class.java)
    val pacenotes = DirtRally2PacenoteAtomAdapter.adapt(codriverData)
    SSMLDocument(Locale.ENGLISH, pacenotes.map { it.toSSML(Locale.ENGLISH) })
        .let { ssmlToString(it, true) }
        .let(::println)

    val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-goog-api-key", System.getenv("GOOGLE_API_KEY"))
                .build()
            chain.proceed(request)
        }
        .callTimeout(java.time.Duration.ofSeconds(10))
        .build()
    val synthesizer = GoogleCloudSpeechSynthesizer(httpClient = httpClient)

    val pacenoteAudio = PacenoteAudio.renderToFile(pacenotes, synthesizer, storeAudioIn = Paths.get("pacenotes.opus"))
    val savedAudio = FlyingNarratorJsonFormat.encodeToString(PacenoteAudio.serializer(), pacenoteAudio)
    Paths.get("pacenote-audio.json").writeText(savedAudio)
}