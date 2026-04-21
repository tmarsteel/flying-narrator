package io.github.tmarsteel.flyingnarrator.nefs

data class NefsFileRef(
    val id: NefsItemId,
    val isDirectory: Boolean,
    val extractedSize: UInt,
    val fileName: String,
    val fullPath: String,
)