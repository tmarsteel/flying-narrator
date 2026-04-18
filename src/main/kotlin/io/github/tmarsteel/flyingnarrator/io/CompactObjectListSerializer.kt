package io.github.tmarsteel.flyingnarrator.io

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeCollection
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
open class CompactObjectListSerializer<T>(
    private val objectSerializer: KSerializer<T>,
) : KSerializer<List<T>> {
    init {
        require(objectSerializer.descriptor.kind == StructureKind.CLASS) {
            "CompactObjectArraySerializer can only be used with classes"
        }
    }

    override val descriptor: SerialDescriptor = listSerialDescriptor(objectSerializer.descriptor)

    private val elementIndices = 0 ..< objectSerializer.descriptor.elementsCount
    private val elementNames: List<String> = elementIndices.map(objectSerializer.descriptor::getElementName)

    override fun serialize(encoder: Encoder, value: List<T>) {
        if (value.isEmpty()) {
            encoder.encodeCollection(descriptor, 0) { }
            return
        }

        encoder.encodeCollection(descriptor, value.size + 1) outerList@{
            encodeSerializableElement(
                StringArraySerializer.descriptor,
                0,
                StringArraySerializer,
                elementNames,
            )

            value.forEachIndexed { listElementIndex, listElement ->
                encodeSerializableElement(
                    ObjectAsArrayOfValuesSerializer.descriptor,
                    0,
                    ObjectAsArrayOfValuesSerializer,
                    listElement,
                )
            }
        }
    }

    override fun deserialize(decoder: Decoder): List<T> {
        return decoder.decodeStructure(descriptor) outerList@{
            var outerListIndex = decodeElementIndex(ArrayOfStringArraysSerializer.descriptor)
            if (outerListIndex == CompositeDecoder.DECODE_DONE) {
                return@outerList emptyList()
            }

            val propNamesInSerialOrder = decodeSerializableElement(ArrayOfStringArraysSerializer.descriptor, 0, StringArraySerializer)
            val elementIndexBySerialIndex = IntArray(elementNames.size) { serialIndex ->
                val propName = propNamesInSerialOrder[serialIndex]
                var elementIndex = elementNames.indexOf(propName)
                if (elementIndex == -1) {
                    elementIndex = CompositeDecoder.UNKNOWN_NAME
                }
                elementIndex
            }
            val objectDeserializer = ObjectAsArrayOfValuesDeserializer(elementIndexBySerialIndex)

            val objects = mutableListOf<T>()
            while (true) {
                outerListIndex = decodeElementIndex(ArrayOfStringArraysSerializer.descriptor)
                if (outerListIndex == CompositeDecoder.DECODE_DONE) {
                    break
                }

                val listElement = decodeSerializableElement(objectDeserializer.descriptor, 0, objectDeserializer, null)
                objects += listElement
            }

            objects
        }
    }

    companion object {
        val MODULE = SerializersModule {
            contextual(CompactObjectListSerializer::class) { args ->
                CompactObjectListSerializer(args[0])
            }
        }
    }

    private inner class ObjectAsArrayOfValuesEncoder(
        val parent: Encoder,
    ) : Encoder by parent {
        override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
            val parentCompositeEncoder = parent.beginCollection(ObjectAsArrayOfValuesSerializer.descriptor, descriptor.elementsCount)
            return object : CompositeEncoder by parentCompositeEncoder {
                override fun endStructure(descriptor: SerialDescriptor) {
                    parentCompositeEncoder.endStructure(ObjectAsArrayOfValuesSerializer.descriptor)
                }

                @ExperimentalSerializationApi
                override fun shouldEncodeElementDefault(
                    descriptor: SerialDescriptor,
                    index: Int
                ): Boolean {
                    return true
                }
            }
        }
    }

    private val ObjectAsArrayOfValuesSerializer = object : SerializationStrategy<T> {
        override val descriptor: SerialDescriptor = StringArraySerializer.descriptor

        override fun serialize(
            encoder: Encoder,
            value: T
        ) {
            objectSerializer.serialize(ObjectAsArrayOfValuesEncoder(encoder), value)
        }
    }

    private inner class ObjectAsArrayOfValuesDecoder(
        val parent: Decoder,
        val elementIndexBySerialIndex: IntArray,
    ) : Decoder by parent {
        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
            val parentCompositeDecoder = parent.beginStructure(ObjectAsArrayOfValuesSerializer.descriptor)
            return object : CompositeDecoder by parentCompositeDecoder {
                override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
                    val serialIndex = parentCompositeDecoder.decodeElementIndex(descriptor)
                    if (serialIndex < 0) {
                        return serialIndex
                    }
                    if (serialIndex > elementIndexBySerialIndex.lastIndex) {
                        return CompositeDecoder.DECODE_DONE
                    }
                    return elementIndexBySerialIndex[serialIndex]
                }

                override fun endStructure(descriptor: SerialDescriptor) {
                    parentCompositeDecoder.endStructure(ObjectAsArrayOfValuesSerializer.descriptor)
                }

                override val serializersModule: SerializersModule get()= parent.serializersModule
            }
        }
    }

    private inner class ObjectAsArrayOfValuesDeserializer(
        val elementIndexBySerialIndex: IntArray,
    ) : DeserializationStrategy<T> {
        override val descriptor: SerialDescriptor = StringArraySerializer.descriptor

        override fun deserialize(decoder: Decoder): T {
            return objectSerializer.deserialize(ObjectAsArrayOfValuesDecoder(decoder, elementIndexBySerialIndex))
        }
    }
}

private val StringArraySerializer = ListSerializer(String.serializer())
private val ArrayOfStringArraysSerializer = ListSerializer(StringArraySerializer)