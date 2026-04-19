@file:OptIn(ExperimentalSerializationApi::class)
package io.github.tmarsteel.flyingnarrator.nefs

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.AutoCloseable
import java.nio.file.Path
import java.nio.file.Paths

class NefsFile(
    val coordinates: NefsCoordinates,
) : AutoCloseable {
    private val nefsEditCliProcess = ProcessBuilder(listOf(
        nefsEditCliBinary.toString(),
        "--file",
        (coordinates as NefsCoordinates.FileOnSystemDisk).path.toString(),
        "commands-from-stdin"
    ))
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    private val nefsProcessWriter = OutputStreamWriter(nefsEditCliProcess.outputStream, Charsets.UTF_8)
    private val nefsProcessReader = BufferedReader(InputStreamReader(nefsEditCliProcess.inputStream, Charsets.UTF_8))

    private fun assureOpen() {
        check(nefsEditCliProcess.isAlive) { "nefsedit-cli process has died" }
    }

    fun listFiles(): List<String> {
        return execute(NefsCommand.EnumerateFiles(), serializer<NefsCommand.EnumerateFiles.Result>()).files
    }

    fun extractFile(nefsPath: String, destination: Path, decodeBinaryXML: Boolean) {
        val response = execute(
            NefsCommand.Extract(nefsPath, destination.toAbsolutePath(), decodeBinaryXML),
            serializer<NefsCommand.Extract.Result>(),
        )
        if (!response.itemLocated) {
            throw FileNotFoundException("$coordinates#$nefsPath")
        }
        if (!response.extractionSuccessful) {
            throw RuntimeException("Could not extract $coordinates#$nefsPath")
        }
    }

    private fun <R> execute(command: NefsCommand, responseDeserializer: DeserializationStrategy<R>): R {
        val commandAsString = CommandFormat.encodeToString(serializer<NefsCommand>(), command)
        val responseString = synchronized(nefsEditCliProcess) {
            assureOpen()
            nefsProcessWriter.write(commandAsString)
            nefsProcessWriter.write("\n")
            nefsProcessWriter.flush()
            nefsProcessReader.readLine()
        }
        return CommandFormat.decodeFromString(responseDeserializer, responseString)
    }

    override fun close() {
        if (!nefsEditCliProcess.isAlive) {
            return
        }
        execute(NefsCommand.Exit(), serializer<Unit>())
        nefsEditCliProcess.waitFor()
    }

    companion object {
        private val CommandFormat = Json {
            prettyPrint = false
            encodeDefaults = false
            explicitNulls = false
        }
        private val nefsEditCliBinary by lazy {
            Paths.get("""F:\CodingProjects\flying-narrator\nefsedit-cli\nefsedit-cli\bin\Debug\net10.0\nefsedit-cli.exe""")
        }
    }
}