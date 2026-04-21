package io.github.tmarsteel.flyingnarrator.nefs

import java.nio.file.Path
import kotlin.io.path.exists

sealed interface NefsCoordinates {
    data class FileOnSystemDisk(val path: Path): NefsCoordinates {
        init {
            require(path.isAbsolute)
        }
    }

    data class Headless(
        val gameExecutable: Path,
        val dataDirectory: Path,
        val dataFile: Path,
        val searchEntireExecutable: Boolean = false,
    ) : NefsCoordinates{
        init {
            require(gameExecutable.isAbsolute)
            require(dataDirectory.isAbsolute)
            require(!dataFile.isAbsolute)
            require(gameExecutable.exists())
            require(dataDirectory.exists())
            require(dataDirectory.resolve(dataFile).exists())
        }
    }
}