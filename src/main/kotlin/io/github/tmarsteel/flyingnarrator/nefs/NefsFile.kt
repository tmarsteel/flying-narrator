@file:OptIn(ExperimentalSerializationApi::class)
package io.github.tmarsteel.flyingnarrator.nefs

import com.sun.jna.Platform
import io.github.tmarsteel.flyingnarrator.nefs.protocol.Command
import io.github.tmarsteel.flyingnarrator.nefs.protocol.itemOrNull
import io.github.tmarsteel.flyingnarrator.nefs.protocol.listedItemsOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import okio.IOException
import java.lang.AutoCloseable
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
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

    fun listDirectoryByPath(path: List<String>, parentDirectory: NefsItemId? = null): List<NefsFileRef> {
        if (path.isEmpty()) {
            return listFiles(false, parentDirectory)
        }

        val nextD = listFiles(false, parentDirectory).find { d -> d.fileName == path.first() }
        if (nextD == null) {
            return emptyList()
        }

        return listDirectoryByPath(path.subList(1, path.size), nextD.id)
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

    fun readFileByPath(path: List<String>, parentDirectory: NefsItemId? = null): ByteBuffer? {
        if (path.isEmpty()) {
            throw NefsException("Path is empty")
        }
        val directoryListing = listDirectoryByPath(path.subList(0, path.size - 1), parentDirectory)
        val fileEntry = directoryListing.find { d -> d.fileName == path.last() }
            ?: return null

        return readFile(fileEntry.id)
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

        private val NEFSEDIT_CLI_RESOURCES_BY_PLATFORM = mapOf(
            "win32-x86-64" to Pair(
                "/nefsedit-cli/win-x64",
                listOf(
                    "nefsedit-cli.exe",
                    "libzstd.dll",
                )
            )
        )

        @OptIn(ExperimentalPathApi::class)
        private val nefsEditCliBinary by lazy {
            System.getenv("NEFSEDIT_CLI_PATH")?.let {
                return@lazy Paths.get(it)
            }

            val (appDir, filesInAppDir) = NEFSEDIT_CLI_RESOURCES_BY_PLATFORM[Platform.RESOURCE_PREFIX]
                ?: throw RuntimeException("nefsedit-cli is not bundled for this platform: ${Platform.RESOURCE_PREFIX}")

            val classpathToFirstFile = "$appDir/${filesInAppDir.first()}"
            val executableResource = NefsFile::class.java.getResource(classpathToFirstFile)
                ?: throw RuntimeException("Could not find $classpathToFirstFile in classpath")

            if (executableResource.protocol == "file") {
                var pathStr = executableResource.path
                if ("win32" in Platform.RESOURCE_PREFIX && pathStr.startsWith("/")) {
                    pathStr = pathStr.substring(1)
                }
                return@lazy Paths.get(pathStr)
            }

            val tmpDir = Files.createTempDirectory("nefsedit-cli")
            for (file in filesInAppDir) {
                val resource = NefsFile::class.java.getResource("$appDir/$file")
                    ?: throw RuntimeException("Could not find $file for nefsedit-cli in classpath")
                val dest = tmpDir.resolve(file)
                Files.copy(resource.openStream(), dest)
            }
            Runtime.getRuntime().addShutdownHook(thread(start = false) {
                tmpDir.deleteRecursively()
            })

            return@lazy tmpDir.resolve(filesInAppDir.first())
        }
    }
}