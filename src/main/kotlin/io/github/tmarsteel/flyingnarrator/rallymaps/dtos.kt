package io.github.tmarsteel.flyingnarrator.rallymaps

import de.micromata.opengis.kml.v_2_2_0.Coordinate
import io.github.tmarsteel.flyingnarrator.geometry.Geospatial2
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
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
    @Serializable(with = GeometriesSerializer::class)
    val geometries: List<GeometryDto>,
) {
    @Serializable(with = Type.Serializer::class)
    enum class Type(val serialValue: Int) {
        RACE(0),
        SHAKEDOWN(2),
        SERVICE_PARK(3),
        PODIUM(7),
        REGROUPING(8),
        PARK_EXPOSE(9),
        PARK_FERME(10),
        CEREMONIAL_START(12),
        CEREMONIAL_FINISH(13),
        UNKNOWN(-1),
        ;

        class Serializer : EnumAsIntSerializer<Type>(Type::class.java, Type::serialValue, UNKNOWN)
    }

    class GeometriesSerializer : KSerializer<List<GeometryDto>> {
        private val geometryDtoSerializer = GeometryDto.serializer()
        private val listSerializer = ListSerializer(geometryDtoSerializer)
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun serialize(
            encoder: Encoder,
            value: List<GeometryDto>
        ) {
            listSerializer.serialize(encoder, value)
        }

        override fun deserialize(decoder: Decoder): List<GeometryDto> {
            decoder as? JsonDecoder ?: error("can only deserialize from JSON")
            val jsonElement = decoder.decodeJsonElement()

            if (jsonElement is JsonArray) {
                return decoder.json.decodeFromJsonElement(listSerializer, jsonElement)
            }

            if (jsonElement is JsonPrimitive && jsonElement.isString && jsonElement.content == "") {
                return emptyList()
            }

            throw SerializationException("Expected array of geometries, got JSON string: $jsonElement")
        }
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
    val coordinates: List<@Serializable(with = Geospatial2Serializer::class) Geospatial2>,
) : GeometryDataDto

@Serializable
@SerialName("Polygon")
data class PolygonDto(
    val coordinates: List<List<@Serializable(with = Geospatial2Serializer::class) Geospatial2>>,
) : GeometryDataDto

@Serializable
@SerialName("Point")
data class PointDto(
    @Serializable(with = Geospatial2Serializer::class)
    val coordinates: Geospatial2,
) : GeometryDataDto

abstract class EnumAsIntSerializer<T : Enum<T>>(
    private val enumClass: Class<T>,
    private val prop: KProperty1<T, Int>,
    val unknownValue: T?,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = Int.serializer().descriptor

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeInt(prop.get(value))
    }

    override fun deserialize(decoder: Decoder): T {
        val value = decoder.decodeInt()
        return enumClass.enumConstants
            .find { prop.get(it) == value }
            ?: unknownValue
            ?: throw SerializationException("Unknown enum value for ${enumClass.name}: $value")
    }
}

@OptIn(ExperimentalSerializationApi::class)
class Geospatial2Serializer : KSerializer<Geospatial2> {
    override val descriptor: SerialDescriptor = listSerialDescriptor(Double.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: Geospatial2
    ) {
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.latitude)
            encodeDoubleElement(descriptor, 1, value.longitude)
        }
    }

    override fun deserialize(decoder: Decoder): Geospatial2 {
        return decoder.decodeStructure(descriptor) {
            var lat = 0.0
            var long = 0.0

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> lat = decodeDoubleElement(descriptor, 0)
                    1 -> long = decodeDoubleElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            Geospatial2(lat, long)
        }
    }
}

@OptIn(InternalSerializationApi::class)
class ElevationDataPointSerializer : KSerializer<Coordinate> {
    override val descriptor = buildSerialDescriptor(Coordinate::class.java.name, SerialKind.ENUM) {
        element("lat", Double.serializer().descriptor)
        element("lng", Double.serializer().descriptor)
        element("ele", Double.serializer().descriptor)
    }

    override fun serialize(
        encoder: Encoder,
        value: Coordinate
    ) {
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.latitude)
            encodeDoubleElement(descriptor, 1, value.longitude)
            encodeDoubleElement(descriptor, 2, value.altitude)
        }
    }

    override fun deserialize(decoder: Decoder): Coordinate {
        return decoder.decodeStructure(descriptor) {
            var lat = 0.0
            var long = 0.0
            var alt = 0.0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> lat = decodeDoubleElement(descriptor, 0)
                    1 -> long = decodeDoubleElement(descriptor, 1)
                    2 -> alt = decodeDoubleElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            Coordinate(long, lat, alt)
        }
    }
}

val ElevationDataSerializer = ListSerializer(ElevationDataPointSerializer())