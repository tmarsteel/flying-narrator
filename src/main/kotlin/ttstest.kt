package io.github.tmarsteel.flyingnarrator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.file.Paths
import kotlin.io.encoding.Base64
import kotlin.io.path.writeBytes

fun main() {
    val ssml = """
        <lang xml:lang="en-US"><prosody rate="fast">
        <mark name="pos0"/>
        50.
        <mark name="pos1"/>
        2 left long.
        <mark name="pos2"/>
        opens over crest.
        <mark name="pos3"/>
        over bridge.
        <mark name="pos4"/>
        tarmac.
        <mark name="pos5"/>
        into 2 right long.
        <mark name="pos6"/>
        opens bad camber.
        <mark name="pos7"/>
        40.
        <mark name="pos8"/>
        5 right.
        <mark name="pos9"/>
        into 4 left long; caution, tightens over bridge.
        <mark name="pos10"/>
        to gravel.
        <mark name="pos11"/>
        30.
        <mark name="pos12"/>
        4 right over crest.
        <mark name="pos13"/>
        into 6 left.
        <mark name="pos14"/>
        80.
        </prosody></lang>
    """.trimIndent()
        .replace("\n", "\\n")
        .replace("\"", "\\\"")
    val hc = OkHttpClient()
    val response = hc.newCall(
        Request.Builder()
            .url("https://texttospeech.googleapis.com/v1/text:synthesize")
            .addHeader("Authorization", "Bearer ${System.getenv("GCLOUD_AUTH_TOKEN")}")
            .addHeader("x-goog-user-project", "ai-tests-476722")
            .post("""
                {
                  "input": {
                    "ssml": "$ssml"
                  },
                  "voice": {
                    "languageCode": "en-gb",
                    "ssmlGender": "MALE"
                  },
                  "audioConfig": {
                    "audioEncoding": "MP3"
                  },
                  "enableTimePointing": [
                    "SSML_MARK"
                  ]
                }
            """.toRequestBody("application/json".toMediaType()))
            .build(),
    )
        .execute()

    println(response.code)
    println(response.headers)
    val rawResponseString = response.body?.string()!!
    println(rawResponseString)
    val responseJson = Json.decodeFromString<JsonElement>(rawResponseString)
    val audioData = responseJson.jsonObject["audioContent"]?.jsonPrimitive?.content?.let(Base64::decode)
    Paths.get("stage.mp3").writeBytes(audioData!!)
}