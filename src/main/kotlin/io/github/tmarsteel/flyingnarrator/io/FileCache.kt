package io.github.tmarsteel.flyingnarrator.io

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
object FileCache {
    val directory: Path = (System.getenv("FLYING_NARRATOR_CACHE_DIRECTORY")?.let(Paths::get)
        ?: System.getenv("LOCALAPPDATA").let(Paths::get)
        ?: System.getenv("HOME").let(Paths::get).resolve(".local"))
        .resolve("FlyingNarrator")
        .resolve("Cache")
        .toAbsolutePath()

    fun <T : Any> get(key: String, deserializer: DeserializationStrategy<T>): T? {
        try {
            entryPath(key).inputStream().use { fileIn ->
                return FlyingNarratorJsonFormat.decodeFromStream(deserializer, fileIn)
            }
        }
        catch (_: NoSuchFileException) {
            return null
        }
        catch (_: java.nio.file.NoSuchFileException) {
            return null
        }
    }
    inline fun <reified T : Any> get(key: String) = get(key, serializer<T>())

    fun <T> set(key: String, value: T, serializer: SerializationStrategy<T>) {
        entryPath(key).let {
            it.parent.createDirectories()
            it.outputStream().use { fileOut ->
                FlyingNarratorJsonFormat.encodeToStream(serializer, value, fileOut)
            }
        }
    }
    inline fun <reified T> set(key: String, value: T) = set(key, value, serializer())

    private fun entryPath(key: String): Path {
        val hash = hash(key)
        return directory.resolve(hash.substring(0, 2)).resolve(hash)
    }

    private val digest = ThreadLocal.withInitial { java.security.MessageDigest.getInstance("MD5") }
    private fun hash(key: String): String {
        val md = digest.get()
        md.reset()
        md.update(key.toByteArray())
        return md.digest().toHexString(HexFormat.Default)
    }
}