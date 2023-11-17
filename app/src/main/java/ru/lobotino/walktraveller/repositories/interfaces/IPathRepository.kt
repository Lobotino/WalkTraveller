package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint

interface IPathRepository {

    suspend fun createNewPath(startPoint: MapPoint): Long

    suspend fun createNewPath(pathsSegments: List<MapPathSegment>): Long?

    suspend fun addNewPathPoint(
        pathId: Long,
        point: MapPoint,
        segmentRating: SegmentRating = SegmentRating.NORMAL
    ): Long

    suspend fun getAllPathsInfo(): List<EntityPath>

    suspend fun getAllPathPoints(pathId: Long): List<EntityPoint>

    suspend fun getAllPathSegments(pathId: Long): List<EntityPathSegment>

    suspend fun getPathStartSegment(pathId: Long): EntityPathSegment?

    suspend fun getLastPathInfo(): EntityPath?

    suspend fun getLastPathSegments(): List<EntityPathSegment>

    suspend fun getPointInfo(pointId: Long): EntityPoint?

    suspend fun deletePath(pathId: Long)

    suspend fun updatePathLength(pathId: Long, length: Float)

    suspend fun updatePathMostCommonRating(pathId: Long, mostCommonRating: MostCommonRating)

}