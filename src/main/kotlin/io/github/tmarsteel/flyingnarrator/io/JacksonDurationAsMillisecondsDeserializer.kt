package io.github.tmarsteel.flyingnarrator.io

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JacksonDurationAsMillisecondsDeserializer : StdDeserializer<Duration>(Duration::class.java) {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): Duration {
        return when (p.currentToken()) {
            JsonToken.VALUE_NUMBER_INT -> p.longValue.milliseconds
            JsonToken.VALUE_NUMBER_FLOAT -> p.doubleValue.milliseconds
            JsonToken.VALUE_STRING -> try {
                p.string.toLong().milliseconds
            } catch (ex: NumberFormatException) {
                ctxt.handleWeirdStringValue(Duration::class.java, p.string, "Not numeric, cannot deserialize duration as milliseconds") as Duration
            }
            else -> ctxt.handleUnexpectedToken(Duration::class.java, p) as Duration
        }
    }
}