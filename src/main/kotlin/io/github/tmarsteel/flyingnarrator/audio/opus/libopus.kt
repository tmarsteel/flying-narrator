package io.github.tmarsteel.flyingnarrator.audio.opus

import com.sun.jna.Platform
import tomp2p.opuswrapper.Opus
import java.io.File
import java.nio.BufferUnderflowException

internal fun throwOnOpusError(result: Int) {
    when (result) {
        Opus.OPUS_OK               -> {}
        Opus.OPUS_BAD_ARG          -> throw IllegalArgumentException("Opus BAD_ARG")
        Opus.OPUS_BUFFER_TOO_SMALL -> throw BufferUnderflowException()
        Opus.OPUS_INTERNAL_ERROR   -> throw RuntimeException("Opus INTERNAL_ERROR")
        Opus.OPUS_INVALID_PACKET   -> throw IllegalArgumentException("Opus INVALID_PACKET")
        Opus.OPUS_UNIMPLEMENTED    -> throw UnsupportedOperationException("Opus UNIMPLEMENTED")
        Opus.OPUS_INVALID_STATE    -> throw IllegalStateException("Opus INVALID_STATE")
        Opus.OPUS_ALLOC_FAIL       -> throw RuntimeException("Opus ALLOC_FAIL")
        else                       -> if (result < 0) {
            throw RuntimeException("Opus UNKNOWN ERROR: $result")
        }
    }
}

private val OPUS_LIB_RESOURCE_BY_PLATFORM = mapOf(
    "win32-x86-64" to "/opus/win-x64/libopus-0.dll"
)

internal fun assureOpusNativeLibraryIsLoadable() {
    if (System.getProperty("opus.lib") != null) {
        return
    }
    System.setProperty("opus.lib", """F:\CodingProjects\audio-network\transmitter\src\main\resources\natives\win32-x86-64\libopus.dll""")
    val libFilePath = OPUS_LIB_RESOURCE_BY_PLATFORM[Platform.RESOURCE_PREFIX]
        ?: throw RuntimeException("opus is not bundled for this platform: ${Platform.RESOURCE_PREFIX}")
    val libFileResource = OggOpusEncoding::class.java.getResource(libFilePath)
        ?: throw RuntimeException("Could not find opus native library at $libFilePath in classpath")

    if (libFileResource.protocol == "file") {
        var pathStr = libFileResource.path
        if ("win32" in Platform.RESOURCE_PREFIX && pathStr.startsWith("/")) {
            pathStr = pathStr.substring(1)
        }
        System.setProperty("opus.lib", pathStr)
        return
    }

    val tmpFile = File.createTempFile("opus", ".dll")
    tmpFile.deleteOnExit()
    tmpFile.outputStream().use { outStream ->
        libFileResource.openStream().use { inStream ->
            inStream.copyTo(outStream)
        }
    }
    System.setProperty("opus.lib", tmpFile.absolutePath)
}