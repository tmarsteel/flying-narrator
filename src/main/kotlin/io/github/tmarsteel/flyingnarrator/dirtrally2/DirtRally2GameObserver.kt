package io.github.tmarsteel.flyingnarrator.dirtrally2

import java.nio.file.Paths
import kotlin.io.path.name

object DirtRally2GameObserver {
    val currentGameProcess: ProcessHandle?
        get() = ProcessHandle.allProcesses()
            .filter { it.isAlive }
            .filter { it.info().command()?.orElse(null)?.let(Paths::get)?.fileName?.name == "dirtrally2.exe" }
            .findAny()
            .orElse(null)
}