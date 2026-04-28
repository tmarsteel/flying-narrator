package io.github.tmarsteel.flyingnarrator.unit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.withSign
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Serializable(with = Distance.Companion.Serializer::class)
@JvmInline
value class Distance(private val meters: Double) : ScalarLike<Distance> {
    override operator fun plus(other: Distance): Distance = Distance(meters + other.meters)
    override operator fun minus(other: Distance): Distance = Distance(meters - other.meters)
    override operator fun times(scalar: Double): Distance = Distance(meters * scalar)

    operator fun div(time: Duration): Velocity = Velocity(meters / time.toDouble(DurationUnit.SECONDS))
    operator fun div(velocity: Velocity): Duration = (meters / velocity.toDoubleAsMetersPerSecond()).seconds
    operator fun div(other: Distance): Double = meters / other.meters
    override operator fun div(scalar: Int): Distance = div(scalar.toDouble())
    override operator fun div(scalar: Double): Distance = Distance(meters / scalar)

    override val sign get()= meters.sign
    override fun withSign(sign: Double): Distance = Distance(meters.withSign(sign))

    override fun compareTo(other: Distance): Int = meters.compareTo(other.meters)

    override fun toString(): String = when {
        meters < 0 -> "-" + (-this).toString()
        meters < 10 -> "%.2fm".format(meters)
        meters < 100 -> meters.roundToInt().toString() + "m"
        else -> "%3.2fkm".format(meters / 1000.0)
    }

    fun toDoubleInMeters(): Double = meters

    companion object {
        val Int.meters: Distance get() = this.toDouble().meters
        val Float.meters: Distance get() = Distance(this.toDouble())
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