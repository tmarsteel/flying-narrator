package io.github.tmarsteel.flyingnarrator.unit

import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.DurationUnit

@JvmInline
value class Velocity(val metersPerSecond: Double) : Comparable<Velocity> {
    operator fun plus(other: Velocity): Velocity = Velocity(metersPerSecond + other.metersPerSecond)
    override fun compareTo(other: Velocity): Int = metersPerSecond.compareTo(other.metersPerSecond)

    operator fun times(time: Duration): Distance = (metersPerSecond * time.toDouble(DurationUnit.SECONDS)).meters
    operator fun div(scalar: Double): Velocity = Velocity(metersPerSecond / scalar)

    companion object {
        object Serializer : KSerializer<Velocity> {
            override val descriptor = PrimitiveSerialDescriptor("Velocity", PrimitiveKind.DOUBLE)

            override fun serialize(
                encoder: Encoder,
                value: Velocity
            ) {
                encoder.encodeDouble(value.metersPerSecond)
            }

            override fun deserialize(decoder: Decoder): Velocity {
                return Velocity(decoder.decodeDouble())
            }
        }
    }
}