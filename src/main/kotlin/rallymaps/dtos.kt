package io.github.tmarsteel.flyingnarrator.rallymaps

import kotlinx.serialization.Serializable

@Serializable
data class RallyDto(
    val name: String,
    val stages: List<StageDto>,
)

@Serializable
data class StageDto(
    val id: Long,
    val name: String,
    val fullName: String,
    val prefix: String,
    val stageType: Int, // TODO: Map!
    val geometries: List<StageGeometryDto>,
) {
    enum class Type(val serialValue: Int) {
        REGROUPING(8),
        SERVICE_PARK(3)
    }
}

@Serializable
class StageGeometryDto(

)