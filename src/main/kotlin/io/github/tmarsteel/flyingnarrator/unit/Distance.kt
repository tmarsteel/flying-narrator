package io.github.tmarsteel.flyingnarrator.unit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Serializable(with = Distance.Companion.Serializer::class)
@JvmInline
value class Distance(val meters: Double) : Comparable<Distance> {
    operator fun plus(other: Distance): Distance = Distance(meters + other.meters)
    operator fun minus(other: Distance): Distance = Distance(meters - other.meters)
    operator fun times(scalar: Double): Distance = Distance(meters * scalar)
    operator fun unaryMinus(): Distance = Distance(-meters)

    operator fun div(time: Duration): Velocity = Velocity(meters / time.toDouble(DurationUnit.SECONDS))
    operator fun div(velocity: Velocity): Duration = (meters / velocity.metersPerSecond).seconds

    override fun compareTo(other: Distance): Int = meters.compareTo(other.meters)

    companion object {
        val Int.meters: Distance get() = this.toDouble().meters
        val Double.meters: Distance get() = Distance(this)

        object Serializer : KSerializer<Distance> {
            override val descriptor = PrimitiveSerialDescriptor("Distance", PrimitiveKind.DOUBLE)

            override fun serialize(
                encoder: Encoder,
                value: Distance
            ) {
                encoder.encodeDouble(value.meters)
            }

            override fun deserialize(decoder: Decoder): Distance {
                return Distance(decoder.decodeDouble())
            }
        }
    }
}