package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2ProgressGate
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2ProgressRouteSplit
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2ProgressTrackData
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2TrackSplines
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2XMLMapper
import io.github.tmarsteel.flyingnarrator.feature.MLine
import io.github.tmarsteel.flyingnarrator.feature.OPTIMAL_ROAD_SEGMENT_LENGTH
import io.github.tmarsteel.flyingnarrator.geometry.HermiteSpline
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.nefs.NefsFile
import io.github.tmarsteel.flyingnarrator.nefs.NefsItemId
import io.github.tmarsteel.flyingnarrator.nefs.protocol.Command
import io.github.tmarsteel.flyingnarrator.route.RoadSegment
import io.github.tmarsteel.flyingnarrator.route.Route
import io.github.tmarsteel.flyingnarrator.route.RouteReader
import io.github.tmarsteel.flyingnarrator.unit.Distance
import io.github.tmarsteel.flyingnarrator.unit.Distance.Companion.meters
import tools.jackson.databind.util.ByteBufferBackedInputStream
import java.nio.file.Path

class DirtRally2RouteReader(
    val splineDto: DR2TrackSplines,
    val progressDto: DR2ProgressTrackData,
) : RouteReader {
    constructor(sourceDir: Path) : this(
        DR2XMLMapper.readValue(sourceDir.resolve("track_spline.xml"), DR2TrackSplines::class.java),
        DR2XMLMapper.readValue(sourceDir.resolve("progress_track.xml"), DR2ProgressTrackData::class.java),
    )

    private val progressRoute = progressDto.routes.single()
    private val startSplit = progressRoute.splits.single { it.type == DR2ProgressRouteSplit.Type.START }
    private val finishSplit = progressRoute.splits.single { it.type == DR2ProgressRouteSplit.Type.FINISH }
    private val startGate = Gate(progressDto.gates.single { it.id == startSplit.gateId })
    private val finishGate = Gate(progressDto.gates.single { it.id == finishSplit.gateId })

    val positionsOnCentralSpline = HermiteSpline.interpolate(
        splineDto.centralSplineOriginal.controlPoints,
        OPTIMAL_ROAD_SEGMENT_LENGTH,
    )

    private val route by lazy {
        val allVectors = positionsOnCentralSpline
            .zipWithNext { pos1, pos2 ->
                pos2 - pos1
            }
            .toMutableList()

        var idxBeforeStart = 0
        var idxAfterFinish = 0
        var positionCarry = positionsOnCentralSpline.first()
        for ((idx, vec) in allVectors.withIndex()) {
            if (startGate.isCrossedBy(positionCarry, vec)) {
                idxBeforeStart = idx
            }
            if (finishGate.isCrossedBy(positionCarry, vec)) {
                idxAfterFinish = idx + 1
            }
            positionCarry += vec
        }

        allVectors
            .subList(idxBeforeStart, idxAfterFinish)
            .map(::RoadSegment)
    }

    override fun read(): Route {
        return route
    }

    companion object {
        fun fromNefs(nefsFile: NefsFile, directoryId: NefsItemId): DirtRally2RouteReader {
            val filesInDir = nefsFile.listFiles(recursive = false, directory = directoryId)
            val splineData = filesInDir
                .single { it.fileName == "route_spline.xml" }
                .let { nefsFile.readFile(it.id, Command.Conversion.UNPACK_BINARY_XML) }
                .let { DR2XMLMapper.readValue(ByteBufferBackedInputStream(it), DR2TrackSplines::class.java) }
            val codriverData = filesInDir
                .single { it.fileName == "progress_track.xml" }
                .let { nefsFile.readFile(it.id, Command.Conversion.UNPACK_BINARY_XML) }
                .let { DR2XMLMapper.readValue(ByteBufferBackedInputStream(it), DR2ProgressTrackData::class.java) }

            return DirtRally2RouteReader(splineData, codriverData)
        }
    }

    private data class Gate(
        val id: Long,
        val left: Vector3,
        val crossing: Vector3,
        val right: Vector3,
        /**
         * The distance that the **game** specifies for this gate; this **will** vary from the distance that you get
         * when summing [RoadSegment.length]s.
         */
        val distanceInGame: Distance,
    ) {
        constructor(dto: DR2ProgressGate) : this(
            dto.id,
            dto.left.let { Vector3(it.z, it.x, it.y) },
            dto.crossing.let { Vector3(it.z, it.x, it.y) },
            dto.right.let { Vector3(it.z, it.x, it.y) },
            dto.distance.meters,
        )

        private val line1 = MLine(left, crossing - left)
        private val line2 = MLine(crossing, right - crossing)

        fun isCrossedBy(startPoint: Vector3, roadSegmentForward: Vector3): Boolean {
            val segmentLine = MLine(startPoint, roadSegmentForward)
            val line1Intersection = line1.intersect2d(segmentLine)
            val line2Intersection = line2.intersect2d(segmentLine)

            return (line1Intersection != null && line1Intersection.second) || (line2Intersection != null && line2Intersection.second)
        }
    }
}