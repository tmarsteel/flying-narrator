@file:OptIn(ExperimentalSerializationApi::class)
package io.github.tmarsteel.flyingnarrator.nefs

import io.github.tmarsteel.flyingnarrator.nefs.protocol.Command
import io.github.tmarsteel.flyingnarrator.nefs.protocol.itemOrNull
import io.github.tmarsteel.flyingnarrator.nefs.protocol.listedItemsOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import java.lang.AutoCloseable
import java.nio.ByteBuffer
import java.nio.file.Paths

class NefsFile(
    val coordinates: NefsCoordinates,
) : AutoCloseable {
    private val nefsEditCliProcess = ProcessBuilder(listOf(
        nefsEditCliBinary.toString(),
        "--file",
        (coordinates as NefsCoordinates.FileOnSystemDisk).path.toString(),
    ))
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    private fun assureOpen() {
        check(nefsEditCliProcess.isAlive) { "nefsedit-cli process has died" }
    }

    fun listFiles(recursive: Boolean, directory: NefsItemId? = null): List<NefsFileRef> {
        val commandBuilder = Command.ListItemsCommand.newBuilder()
            .setRecursive(recursive)
        if (directory != null) {
            commandBuilder.setDirectoryId(directory.id.toInt())
        }

        val toHelper = Command.ToNefsEdit.newBuilder()
            .setListItems(commandBuilder.build())
            .build()

        val response = exchange(toHelper)
        val protoItems = response.listedItemsOrNull
            ?: throw NefsException("Improper response from nefsedit-cli; missing listedItems")

        return protoItems.itemsList.map {
            NefsFileRef(NefsItemId(it.id.toUInt()), it.size.toUInt(), it.fileName, it.fullPath)
        }
    }

    fun readFile(id: NefsItemId): ByteBuffer {
        val toHelper = Command.ToNefsEdit.newBuilder()
            .setReadItem(Command.ReadItemCommand.newBuilder()
                .setId(id.id.toInt())
                .build())
            .build()

        val response = exchange(toHelper)
        val protoItem = response.itemOrNull
            ?: throw NefsException("Improper response from nefsedit-cli; missing item")

        return protoItem.data.asReadOnlyByteBuffer()
    }

    private fun exchange(toHelper: Command.ToNefsEdit): Command.FromNefsEdit {
        val response = synchronized(nefsEditCliProcess) {
            assureOpen()
            toHelper.writeDelimitedTo(nefsEditCliProcess.outputStream)
            nefsEditCliProcess.outputStream.flush()
            Command.FromNefsEdit.parseDelimitedFrom(nefsEditCliProcess.inputStream)
        }

        if (response.hasError()) {
            throw NefsException(response.error.message)
        }

        return response
    }

    override fun close() {
        if (!nefsEditCliProcess.isAlive) {
            return
        }
        exchange(
            Command.ToNefsEdit.newBuilder()
                .setExit(Command.ExitCommand.newBuilder().build())
                .build()
        )
        nefsEditCliProcess.waitFor()
    }

    companion object {
        private val nefsEditCliBinary by lazy {
            Paths.get("""F:\CodingProjects\flying-narrator\nefsedit-cli\nefsedit-cli\bin\Debug\net10.0\nefsedit-cli.exe""")
        }
    }
}