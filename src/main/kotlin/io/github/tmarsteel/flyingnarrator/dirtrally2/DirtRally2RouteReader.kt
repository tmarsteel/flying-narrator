package io.github.tmarsteel.flyingnarrator.dirtrally2

import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2ProgressGate
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2ProgressRouteSplit
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2ProgressTrackData
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2TrackProgressPosition
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2TrackSplines
import io.github.tmarsteel.flyingnarrator.dirtrally2.gamemodels.DR2XMLMapper
import io.github.tmarsteel.flyingnarrator.feature.MLine
import io.github.tmarsteel.flyingnarrator.feature.OPTIMAL_ROAD_SEGMENT_LENGTH
import io.github.tmarsteel.flyingnarrator.geometry.HermiteSpline
import io.github.tmarsteel.flyingnarrator.geometry.Vector3
import io.github.tmarsteel.flyingnarrator.nefs.NefsFile
import io.github.tmarsteel.flyingnarrator.nefs.NefsItemId
import io.github.tmarsteel.flyingnarrator.nefs.protocol.Command
import io.github.tmarsteel.flyingnarrator.route.RouteDto
import io.github.tmarsteel.flyingnarrator.route.RouteReader
import io.github.tmarsteel.flyingnarrator.route.RouteSegment
import io.github.tmarsteel.flyingnarrator.route.RouteSegmentDto
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
    private val startSplit = progressRoute.splits.first { it.type == DR2ProgressRouteSplit.Type.START }
    private val finishSplit = progressRoute.splits.last { it.type == DR2ProgressRouteSplit.Type.FINISH }
    private val startGateDto = progressDto.gates.single { it.id == startSplit.gateId }
    private val finishGateDto = progressDto.gates.single { it.id == finishSplit.gateId }
    private val startGate = Gate(startGateDto)
    private val finishGate = Gate(finishGateDto)

    val positionsOnCentralSpline = HermiteSpline.interpolate(
        splineDto.centralSplineOriginal.controlPoints,
        OPTIMAL_ROAD_SEGMENT_LENGTH,
    )

    val startPosition: DR2TrackProgressPosition = startGateDto.crossing

    private val routeDto by lazy {
        val allVectors = positionsOnCentralSpline
            .zipWithNext { pos1, pos2 ->
                pos2 - pos1
            }
            .toMutableList()

        var idxBeforeStart = 0
        var idxBeforeFinish = 0
        var finishExtraDistance = 0.0
        var positionCarry = positionsOnCentralSpline.first()
        for ((idx, vec) in allVectors.withIndex()) {
            if (startGate.getCrossingDistance(positionCarry, vec) != null) {
                idxBeforeStart = idx
            }
            finishGate.getCrossingDistance(positionCarry, vec)?.let {
                idxBeforeFinish = idx
                finishExtraDistance = it
            }
            positionCarry += vec
        }

        val segments = allVectors
            .subList(idxBeforeStart, allVectors.size)
            .map(::RouteSegmentDto)

        RouteDto(segments, (allVectors.subList(idxBeforeStart, idxBeforeFinish + 1).sumOf { it.length } + finishExtraDistance).meters)
    }

    override fun read(): RouteDto {
        return routeDto
    }

    companion object {
        fun fromNefs(nefsFile: NefsFile, directoryId: NefsItemId): DirtRally2RouteReader {
            val filesInDir = nefsFile.listFiles(recursive = false, directory = directoryId)
            val splineData = filesInDir
                .single { it.fileName == "track_spline.xml" }
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
         * when summing [RouteSegment.length]s.
         */
        val distanceInGame: Distance,
    ) {
        constructor(dto: DR2ProgressGate) : this(
            dto.id,
            dto.left.let(DirtRally2CoordinateSystem::toAppSystem),
            dto.crossing.let(DirtRally2CoordinateSystem::toAppSystem),
            dto.right.let(DirtRally2CoordinateSystem::toAppSystem),
            dto.distance.meters,
        )

        private val line1 = MLine(left, crossing - left)
        private val line2 = MLine(crossing, right - crossing)

        /**
         * Determines whether the line segment formed by [startPoint] and [roadSegmentForward] crosses this
         * gate.
         * @return the distance along [roadSegmentForward] at which this gate is crossed, or `null` if the given
         * line segment doesn't cross this gate.
         */
        fun getCrossingDistance(startPoint: Vector3, roadSegmentForward: Vector3): Double? {
            val segmentLine = MLine(startPoint, roadSegmentForward)
            val line1Intersection = line1.intersect2d(segmentLine)
            val line2Intersection = line2.intersect2d(segmentLine)

            // TODO: correctly incorporate the Y dimension

            val intersectionPoint = line1Intersection?.let { (p, onSegment) -> p.takeIf { onSegment } }
                ?: line2Intersection?.let { (p, onSegment) -> p.takeIf { onSegment } }

            return intersectionPoint?.let { (it - startPoint).length2d }
        }
    }
}