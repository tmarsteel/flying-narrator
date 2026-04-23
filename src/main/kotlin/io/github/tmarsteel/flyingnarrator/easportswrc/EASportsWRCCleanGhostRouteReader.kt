package io.github.tmarsteel.flyingnarrator.easportswrc

import io.github.tmarsteel.flyingnarrator.RoadSegment
import io.github.tmarsteel.flyingnarrator.Route
import io.github.tmarsteel.flyingnarrator.RouteReader
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Usage: play the stage in the context of a club. Don't race, but follow the middle of the road as closely
 * as you can. Then, go to racenet.com and open the leaderboard for the stage. You can view your own replay.
 * Use the browsers developer tools to save the replay data from the network traffic
 * (e.g. https://web-api.racenet.com/api/wrc2023Stats/performanceAnalysis/ghost?LeaderboardId=4YC5PUATeRWNcfdGa&WrcPlayerId=2LAK99H96VTU5XVbc)
 */
class EASportsWRCCleanGhostRouteReader(
    private val ghost: RacenetStagePlayerGhostDto,
) : RouteReader {
    constructor(cleanGhostReplayJson: String) : this(
        ghost = JSON_FORMAT.decodeFromString<RacenetStageGhostResponseDto>(cleanGhostReplayJson)
            .data
            .player
    )

    private val route by lazy {
        val launchSequenceLength = ghost.distances
            .asSequence()
            .takeWhile { it == 0.0 }
            .count()

        ghost.positions
            .asSequence()
            .drop(launchSequenceLength - 1)
            .windowed(size = 2, step = 1, partialWindows = false)
            .map { (pos, nextPos) ->
                Vector3(
                    // the x/y/z assocs are correct, the game has different axis
                    x = nextPos.z - pos.z,
                    y = nextPos.x - pos.x,
                    z = nextPos.y - pos.y,
                )
            }
            .map { it * GAME_COORDINATE_UNITS_OVER_DISTANCE }
            .map(::RoadSegment)
            .toList()
    }

    override fun read(): Route {
        return route
    }

    companion object {
        /**
         * how [Vector3.length] from the coordinates in [RacenetStagePlayerGhostDto.positions] relates
         * to [RacenetStagePlayerGhostDto.distances]
         * TODO: calibrate better, currently based only on the #22 cleanghost
         */
        const val GAME_COORDINATE_UNITS_OVER_DISTANCE = 1.0

        /**
         * length of one in-game coordinate unit in meters
         */
        const val GAME_COORDINATE_UNITS_OVER_METERS = 1.0
        val JSON_FORMAT = Json {
            ignoreUnknownKeys = true
        }
    }
}

@Serializable
class RacenetStageGhostResponseDto(
    val data: RacenetStageGhostResponseDataDto,
)

@Serializable
class RacenetStageGhostResponseDataDto(
    val player: RacenetStagePlayerGhostDto,
    val rival: RacenetStagePlayerGhostDto? = null,
)

@Serializable
class RacenetStagePlayerGhostDto(
    val minDistance: Double,
    val maxDistance: Double,
    @SerialName("distance")
    val distances: Array<Double>,
    @SerialName("position")
    val positions: Array<PositionDto>,
) {
    @Serializable
    data class PositionDto(
        val x: Double,
        val y: Double,
        val z: Double,
    )
}