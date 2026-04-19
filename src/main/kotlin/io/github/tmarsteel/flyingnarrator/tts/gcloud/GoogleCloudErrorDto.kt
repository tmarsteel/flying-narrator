package io.github.tmarsteel.flyingnarrator.tts.gcloud

import kotlinx.serialization.Serializable

@Serializable
data class GoogleCloudErrorDto(
    val code: Int,
    val message: String,
    val status: String,
)