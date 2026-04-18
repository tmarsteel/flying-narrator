package io.github.tmarsteel.flyingnarrator.http

import okio.FileNotFoundException
import java.net.URL
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.io.path.readText
import kotlin.io.path.writeText

class CachingUrlReader(
    val cacheDir: Path,
    val upstream: (URL) -> String,
) : (URL) -> String {
    private fun toKey(url: URL): String {
        return md5digest.digest(url.toString().toByteArray(charset = Charsets.UTF_8)).toHexString()
    }

    override fun invoke(url: URL): String {
        val cacheKey = toKey(url)
        val cacheFile = cacheDir.resolve(cacheKey)
        try {
            return cacheFile.readText(Charsets.UTF_8)
        } catch (_: FileNotFoundException) {
            // cache empty
        } catch (_: NoSuchFileException) {
            // cache empty
        } catch (_: java.nio.file.NoSuchFileException) {
            // cache empty
        }

        val content = upstream(url)
        try {
            cacheFile.writeText(content, Charsets.UTF_8, StandardOpenOption.CREATE_NEW)
        } catch (_: FileAlreadyExistsException) {
            // race condition, ignore
        }

        return content
    }

    private companion object {
        val md5digest = MessageDigest.getInstance("MD5")
    }
}