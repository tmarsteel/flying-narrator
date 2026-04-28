package io.github.tmarsteel.flyingnarrator.io

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.InetAddress
import java.net.InetSocketAddress

class InetSocketAddressSerializer : KSerializer<InetSocketAddress> {
    override val descriptor = PrimitiveSerialDescriptor("InetSocketAddress", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: InetSocketAddress) {
        encoder.encodeString(value.address.hostAddress + ":" + value.port)
    }

    override fun deserialize(decoder: Decoder): InetSocketAddress {
        val asString = decoder.decodeString()
        val lastColon = asString.lastIndexOf(':')
        val host = asString.substring(0, lastColon)
        val port = asString.substring(lastColon + 1).toInt()
        return InetSocketAddress(InetAddress.getByName(host), port)
    }
}