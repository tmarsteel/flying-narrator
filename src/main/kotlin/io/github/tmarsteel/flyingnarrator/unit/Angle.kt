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

@Serializable
@JvmInline
value class Angle(private val radians: Double) : ScalarLike<Angle> {
    override fun compareTo(other: Angle): Int = radians.compareTo(other.radians)

    override operator fun plus(other: Angle): Angle = Angle(radians + other.radians)
    override operator fun minus(other: Angle): Angle = Angle(radians - other.radians)
    override operator fun div(scalar: Double): Angle = Angle(radians / scalar)
    override operator fun times(scalar: Double): Angle = Angle(radians * scalar)
    override operator fun times(scalar: Int): Angle = times(scalar.toDouble())

    override val sign: Double get()= radians.sign
    override fun withSign(sign: Double): Angle = Angle(radians.withSign(sign))

    override fun toString() = toDoubleInDegrees().roundToInt().toString() + "°"

    fun toDoubleInRadians(): Double = radians
    fun toFloatInRadians(): Float = radians.toFloat()
    fun toDoubleInDegrees(): Double = Math.toDegrees(radians)
    fun toFloatInDegrees(): Float = Math.toDegrees(radians).toFloat()

    companion object {
        val Int.radians: Angle get() = this.toDouble().radians
        val Double.radians: Angle get() = Angle(this)

        val Int.degrees: Angle get()= this.toDouble().degrees
        val Double.degrees: Angle get()= Angle(Math.toRadians(this))

        object Serializer : KSerializer<Angle> {
            override val descriptor = PrimitiveSerialDescriptor("Angle", PrimitiveKind.DOUBLE)

            override fun serialize(
                encoder: Encoder,
                value: Angle
            ) {
                encoder.encodeDouble(value.radians)
            }

            override fun deserialize(decoder: Decoder): Angle {
                return Angle(decoder.decodeDouble())
            }
        }
    }
}