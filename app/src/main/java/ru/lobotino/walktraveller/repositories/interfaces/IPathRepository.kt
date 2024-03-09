package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.database.model.EntityMapPathSegment
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.interop.PointWithRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint

interface IPathRepository {

    suspend fun createNewPath(
        startPoint: MapPoint,
        isOuterPath: Boolean,
        timestamp: Long,
        pathLength: Float = 0f,
        mostCommonRating: MostCommonRating = MostCommonRating.UNKNOWN,
    ): Long

    suspend fun createOuterNewPath(
        pathsSegments: List<MapPathSegment>,
        timestamp: Long,
        pathLength: Float = 0f,
        mostCommonRating: MostCommonRating = MostCommonRating.UNKNOWN,
    ): Long?

    suspend fun addNewPathPoint(
        pathId: Long,
        point: MapPoint,
        segmentRating: SegmentRating = SegmentRating.NORMAL,
        timestamp: Long
    ): Long

    suspend fun addNewPathPoints(
        pathId: Long,
        points: List<PointWithRating>
    ): List<Long>

    suspend fun getAllPathsInfo(): List<EntityPath>

    suspend fun getAllPathPoints(pathId: Long): List<EntityPoint>

    suspend fun getAllPathSegments(pathId: Long): List<EntityMapPathSegment>

    suspend fun getPathStartSegment(pathId: Long): EntityPathSegment?

    suspend fun getLastPathInfo(): EntityPath?

    suspend fun getPointInfo(pointId: Long): EntityPoint?

    suspend fun deletePath(pathId: Long)

    suspend fun deletePaths(pathIds: List<Long>)

    suspend fun updatePathLength(pathId: Long, length: Float)

    suspend fun updatePathMostCommonRating(pathId: Long, mostCommonRating: MostCommonRating)
}
