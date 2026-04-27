package io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels

import com.google.protobuf.Parser
import io.github.tmarsteel.flyingnarrator.io.skipNBytes
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap

class CTPKFile(
    val strings: Map<Int, String>,
    val sections: Map<UInt, Section>,
) {
    private val typedCache = HashMap<UInt, IdentityHashMap<TypedSection<*>, WeakReference<List<*>>>>()

    fun <T> getTypedSection(section: TypedSection<T>): List<T> {
        val byType = typedCache.computeIfAbsent(section.id) { IdentityHashMap() }
        val cached = byType.get(section)?.get()
        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            return cached as List<T>
        }

        val unparsed = sections[section.id] ?: throw IllegalArgumentException("Section $section not found")
        val parsed = unparsed.objects.map { section.deserialize(it.value) }
        byType.put(section, WeakReference(parsed))
        return parsed
    }

    class Section(
        val id: UInt,
        val objects: Map<UInt, ByteBuffer>,
    )

    open class TypedSection<T>(
        val id: UInt,
        val deserialize: (ByteBuffer) -> T,
    ) {
        constructor(id: UInt, parser: Parser<T>) : this(id, parser::parseFrom)
    }

    companion object {
        fun parse(data: ByteBuffer): CTPKFile {
            if (data.order() != ByteOrder.LITTLE_ENDIAN) {
                return parse(data.order(java.nio.ByteOrder.LITTLE_ENDIAN))
            }

            val magic = data.getInt()
            if (magic != 0x4B505443) {
                throw IllegalArgumentException("Invalid magic: $magic")
            }

            data.skipNBytes(8) // unknown
            val stringsOffset = data.getInt()
            val sectionsOffset = data.getInt()

            data.position(stringsOffset)
            val strings = parseStrings(data)

            data.position(sectionsOffset)
            val sections = parseSections(data)

            return CTPKFile(strings, sections)
        }

        private fun parseStrings(data: ByteBuffer): Map<Int, String> {
            val nStrings = data.getInt()
            val strings = HashMap<Int, String>(nStrings)
            check(nStrings >= 0)
            repeat(nStrings) {
                val length = data.getInt()
                val stringData = ByteArray(length)
                data.get(stringData)
                val string = String(stringData, Charsets.UTF_8)
                val hash = hashString(stringData)
                strings.putIfAbsent(hash, string)
            }

            return strings
        }

        private fun parseSections(data: ByteBuffer): Map<UInt, Section> {
            val headers = mutableListOf<SectionHeader>()
            val nSections = data.getInt()
            check(nSections >= 0)
            repeat(nSections) {
                val id = data.getInt()
                val offset = data.getInt()
                headers.add(SectionHeader(id.toUInt(), offset))
            }

            return headers.associate { it.id to parseSection(data, it) }
        }

        private fun parseSection(data: ByteBuffer, header: SectionHeader): Section {
            if (header.offset.toUInt() == 0xFFFFFFFFu) {
                return Section(header.id, emptyMap())
            }

            data.position(header.offset)
            val nObjects = data.getInt()
            val objects = HashMap<UInt, ByteBuffer>(nObjects)
            repeat(nObjects) {
                val id = data.getInt()
                val length = data.getInt()
                val objectData = data.slice(data.position(), length)
                    .order(ByteOrder.LITTLE_ENDIAN)
                check(objectData.remaining() == length)
                objects.putIfAbsent(id.toUInt(), objectData)
                data.skipNBytes(length)
            }

            return Section(header.id, objects)
        }

        private fun hashString(data: ByteArray): Int {
            var v = 0x1505;
            for (b in data) {
                v = v * 33 + b
            }
            return v
        }

        @JvmRecord
        private data class SectionHeader(val id: UInt, val offset: Int)
    }
}

data object CtpkTypes {
    data object BASE {
        val VEHICLE_CLASSES = CTPKFile.TypedSection(0x2442A3BAu, BaseCTPK.VehicleClass.parser())
        val TRACK_MODELS = CTPKFile.TypedSection(0xB8E12AAAu, BaseCTPK.TrackModel.parser())
    }
}