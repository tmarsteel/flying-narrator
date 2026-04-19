package io.github.tmarsteel.flyingnarrator.tts.gcloud

class GoogleCloudAPIException(val error: GoogleCloudErrorDto) : RuntimeException(error.message)