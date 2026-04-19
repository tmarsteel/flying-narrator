package io.github.tmarsteel.flyingnarrator.nefs

import java.nio.file.Path

sealed interface NefsCoordinates {
    data class FileOnSystemDisk(val path: Path): NefsCoordinates {
        init {
            require(path.isAbsolute)
        }
    }
}