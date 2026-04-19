@file:OptIn(ExperimentalSerializationApi::class)
@file:UseSerializers(SystemPathSerializer::class)
package io.github.tmarsteel.flyingnarrator.nefs

import io.github.tmarsteel.flyingnarrator.io.SystemPathSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import java.nio.file.Path

@Serializable
@Polymorphic
@JsonClassDiscriminator("op")
sealed interface NefsCommand {
    @Serializable
    @SerialName("ls")
    class EnumerateFiles : NefsCommand {
        @Serializable
        data class Result(
            val files: List<String>,
        )
    }

    @Serializable
    @SerialName("extract")
    class Extract(
        val pathInNefs: String,
        val extractTo: Path,
        val decodeBinaryXml: Boolean,
    ) : NefsCommand {

        @Serializable
        data class Result(
            val itemLocated: Boolean,
            val extractionSuccessful: Boolean,
        )
    }

    @Serializable
    @SerialName("exit")
    class Exit : NefsCommand
}