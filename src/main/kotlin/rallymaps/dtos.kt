package io.github.tmarsteel.flyingnarrator.rallymaps

import io.github.tmarsteel.flyingnarrator.Vector2
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.reflect.KProperty1

@Serializable
data class RallyDto(
    val name: String,
    val stages: List<StageDto>,
)

@Serializable
data class StageDto(
    val id: Long,
    val name: String,
    val fullName: String,
    val prefix: String,
    val stageType: Type,
    val geometries: List<GeometryDto>,
) {
    @Serializable(with = Type.Serializer::class)
    enum class Type(val serialValue: Int) {
        REGROUPING(8),
        SERVICE_PARK(3),
        RACE(0),
        PARK_FERME(10),
        PARK_EXPOSE(9),
        CEREMONIAL_START(12),
        CEREMONIAL_FINISH(13),
        ;

        class Serializer : EnumAsIntSerializer<Type>(Type::class.java, Type::serialValue)
    }
}

@Serializable
data  class GeometryDto(
    val geometry: GeometryDataDto,
)

@Serializable
@JsonClassDiscriminator("type")
sealed interface GeometryDataDto

@Serializable
@SerialName("LineString")
data class LineStringDto(
    val coordinates: List<@Serializable(with = Vector2Serializer::class) Vector2>,
) : GeometryDataDto

@Serializable
@SerialName("Polygon")
data class PolygonDto(
    val coordinates: List<List<@Serializable(with = Vector2Serializer::class) Vector2>>,
) : GeometryDataDto

@Serializable
@SerialName("Point")
data class PointDto(
    @Serializable(with = Vector2Serializer::class)
    val coordinates: Vector2,
) : GeometryDataDto

abstract class EnumAsIntSerializer<T : Enum<T>>(
    private val enumClass: Class<T>,
    private val prop: KProperty1<T, Int>,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = Int.serializer().descriptor

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeInt(prop.get(value))
    }

    override fun deserialize(decoder: Decoder): T {
        val value = decoder.decodeInt()
        return enumClass.enumConstants
            .find { prop.get(it) == value }
            ?: throw SerializationException("Unknown enum value: $value")
    }
}

@OptIn(ExperimentalSerializationApi::class)
class Vector2Serializer : KSerializer<Vector2> {
    override val descriptor: SerialDescriptor = listSerialDescriptor(Double.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: Vector2
    ) {
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.x)
            encodeDoubleElement(descriptor, 1, value.x)
        }
    }

    override fun deserialize(decoder: Decoder): Vector2 {
        return decoder.decodeStructure(descriptor) {
            var x = 0.0
            var y = 0.0

            if (decodeSequentially()) {
                x = decodeDoubleElement(descriptor, 0)
                y = decodeDoubleElement(descriptor, 1)
            } else {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> x = decodeDoubleElement(descriptor, 0)
                        1 -> y = decodeDoubleElement(descriptor, 1)
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
            }

            Vector2(x, y)
        }
    }
}