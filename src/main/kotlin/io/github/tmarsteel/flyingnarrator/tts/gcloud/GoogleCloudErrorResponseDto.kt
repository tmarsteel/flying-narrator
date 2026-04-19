package io.github.tmarsteel.flyingnarrator.tts.gcloud

import kotlinx.serialization.Serializable

@Serializable
data class GoogleCloudErrorResponseDto(
    val error: GoogleCloudErrorDto
)