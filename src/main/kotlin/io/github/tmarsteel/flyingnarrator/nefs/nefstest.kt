package io.github.tmarsteel.flyingnarrator.nefs

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.Paths
import kotlin.io.path.outputStream

fun main() {
    val gameDir = Paths.get("""F:\Games\SteamGames\SteamApps\common\DiRT Rally 2.0""")
    val coords = NefsCoordinates.Headless(
        gameDir.resolve("dirtrally2.exe"),
        gameDir.resolve("game"),
        Paths.get("game_1.dat"),
    )
    NefsFile.open(coords).use { archive ->
        val firstXml = archive.listFiles(recursive = true)
            .asSequence()
            .filter { it.fileName.endsWith(".xml") }
            .first()

        println(firstXml)

        Paths.get("first.xml").outputStream().use {
            archive.readFile(firstXml.id).writeTo(it)
        }
    }
}

fun ByteBuffer.writeTo(out: OutputStream, buffer: ByteArray = ByteArray(4096)) {
    while (hasRemaining()) {
        val size = minOf(remaining(), buffer.size)
        get(buffer, 0, size).also { out.write(buffer, 0, size) }
    }
}