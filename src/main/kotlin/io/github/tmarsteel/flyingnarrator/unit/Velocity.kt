package io.github.tmarsteel.flyingnarrator.unit

import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.sign
import kotlin.math.withSign
import kotlin.time.Duration
import kotlin.time.DurationUnit

@JvmInline
value class Velocity(private val metersPerSecond: Double) : ScalarLike<Velocity> {
    override operator fun plus(other: Velocity): Velocity = Velocity(metersPerSecond + other.metersPerSecond)
    override fun minus(other: Velocity): Velocity = Velocity(metersPerSecond - other.metersPerSecond)

    override fun compareTo(other: Velocity): Int = metersPerSecond.compareTo(other.metersPerSecond)

    operator fun times(time: Duration): Distance = (metersPerSecond * time.toDouble(DurationUnit.SECONDS)).meters
    override operator fun times(scalar: Double): Velocity = Velocity(metersPerSecond * scalar)
    override operator fun div(scalar: Double): Velocity = Velocity(metersPerSecond / scalar)

    override val sign: Double get()= metersPerSecond.sign
    override fun withSign(sign: Double): Velocity = Velocity(metersPerSecond.withSign(sign))

    fun toDoubleAsMetersPerSecond(): Double = metersPerSecond

    companion object {
        val Int.metersPerSecond: Velocity get() = Velocity(this.toDouble())
        val Float.metersPerSecond: Velocity get() = Velocity(this.toDouble())

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