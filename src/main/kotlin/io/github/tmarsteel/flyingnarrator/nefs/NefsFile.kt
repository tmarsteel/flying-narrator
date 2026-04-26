@file:OptIn(ExperimentalSerializationApi::class)
package io.github.tmarsteel.flyingnarrator.nefs

import io.github.tmarsteel.flyingnarrator.nefs.protocol.Command
import io.github.tmarsteel.flyingnarrator.nefs.protocol.itemOrNull
import io.github.tmarsteel.flyingnarrator.nefs.protocol.listedItemsOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import okio.IOException
import java.lang.AutoCloseable
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NefsFile private constructor(
    private val serviceProcess: Process,
) : AutoCloseable {
    private fun assureOpen() {
        check(serviceProcess.isAlive) { "nefsedit-cli process has died" }
    }

    init {
        assureOpen()
    }

    fun listFiles(recursive: Boolean, directory: NefsItemId? = null): List<NefsFileRef> {
        val toHelper = Command.ToNefsEdit.newBuilder()
            .setListItems(Command.ListItemsCommand.newBuilder()
                .setRecursive(recursive)
                .setNullableOptionalProto(Command.ListItemsCommand.Builder::setDirectoryId, directory?.id?.toInt())
                .build())
            .build()

        val response = exchange(toHelper)
        val protoItems = response.listedItemsOrNull
            ?: throw NefsException("Improper response from nefsedit-cli; missing listedItems")

        return protoItems.itemsList.map {
            NefsFileRef(
                NefsItemId(it.id.toUInt()),
                it.isDirectory,
                it.size.toUInt(),
                it.fileName,
                it.fullPath,
            )
        }
    }

    fun readFile(id: NefsItemId, convert: Command.Conversion? = null): ByteBuffer {
        val toHelper = Command.ToNefsEdit.newBuilder()
            .setReadItem(Command.ReadItemCommand.newBuilder()
                .setId(id.id.toInt())
                .setNullableOptionalProto(Command.ReadItemCommand.Builder::setConvert, convert)
                .build())
            .build()

        val response = exchange(toHelper)
        val protoItem = response.itemOrNull
            ?: throw NefsException("Improper response from nefsedit-cli; missing item")

        return protoItem.data.asReadOnlyByteBuffer()
    }

    private fun exchange(toHelper: Command.ToNefsEdit): Command.FromNefsEdit {
        val response = synchronized(serviceProcess) {
            assureOpen()
            toHelper.writeDelimitedTo(serviceProcess.outputStream)
            serviceProcess.outputStream.flush()
            Command.FromNefsEdit.parseDelimitedFrom(serviceProcess.inputStream)
        }

        if (response.hasError()) {
            throw NefsException(response.error.message)
        }

        return response
    }

    override fun close() {
        tryCleanExitAndDestroyAfterTimeout(serviceProcess, 2.seconds)
    }

    companion object {
        fun open(coordinates: NefsCoordinates): NefsFile {
            val command = buildCommand(coordinates)
            val serviceProcess = try {
                ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
            } catch (ex: IOException) {
                throw NefsException("Failed to start service process", ex)
            }

            val firstOutput = try {
                Command.FromNefsEdit.parseDelimitedFrom(serviceProcess.inputStream)
            } catch (ex: IOException) {
                tryCleanExitAndDestroyAfterTimeout(serviceProcess, 1.seconds)
                throw NefsException("Failed to communicate with service process", ex)
            }
            if (firstOutput.hasError()) {
                tryCleanExitAndDestroyAfterTimeout(serviceProcess, 1.seconds)
                throw NefsException("The service process failed to open the file: " + firstOutput.error.message)
            }
            if (!firstOutput.hasOpenAck()) {
                tryCleanExitAndDestroyAfterTimeout(serviceProcess, 1.seconds)
                throw NefsException("Unsupported first response from nefsedit-cli; missing ${Command.FileOpenAcknowledgement::class.simpleName}")
            }
            if (!firstOutput.openAck.hasSuccess() || !firstOutput.openAck.success) {
                tryCleanExitAndDestroyAfterTimeout(serviceProcess, 1.seconds)
                throw NefsException("The service process did not acknowledge file opening")
            }

            return NefsFile(serviceProcess)
        }

        private val nefsEditCliBinary by lazy {
            Paths.get("""F:\CodingProjects\flying-narrator\nefsedit-cli\nefsedit-cli\bin\Release\net10.0\nefsedit-cli.exe""")
        }

        private fun buildCommand(coordinates: NefsCoordinates): List<String> {
            return when(coordinates) {
                is NefsCoordinates.FileOnSystemDisk -> listOf(
                    nefsEditCliBinary.toString(),
                    "--file",
                    coordinates.path.toString(),
                )
                is NefsCoordinates.Headless -> listOf(
                    nefsEditCliBinary.toString(),
                    "--game-executable",
                    coordinates.gameExecutable.toString(),
                    "--data-directory",
                    coordinates.dataDirectory.toString(),
                    "--data-file",
                    coordinates.dataFile.toString(),
                    "--search-entire-executable",
                    coordinates.searchEntireExecutable.toString(),
                )
            }
        }

        private fun tryCleanExitAndDestroyAfterTimeout(serviceProcess: Process, timeout: Duration) {
            if (!serviceProcess.isAlive) {
                return
            }

            try {
                Command.ToNefsEdit.newBuilder()
                    .setExit(Command.ExitCommand.newBuilder().build())
                    .build()
                    .writeDelimitedTo(serviceProcess.outputStream);
                serviceProcess.outputStream.flush()
                serviceProcess.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            }
            catch (_: IOException) {}
            catch (_: InterruptedException) { }

            serviceProcess.destroy()
        }
    }
}