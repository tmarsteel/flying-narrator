package io.github.tmarsteel.flyingnarrator.io

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64

class ByteArrayBase64Serializer : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("Base64", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(
            Base64.getEncoder().encodeToString(value)
        )
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        return Base64.getDecoder().decode(
            decoder.decodeString()
        )
    }
}