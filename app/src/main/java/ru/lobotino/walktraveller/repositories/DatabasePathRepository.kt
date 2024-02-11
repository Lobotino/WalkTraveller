package ru.lobotino.walktraveller.repositories

import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathPointRelation
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.ILastCreatedPathIdRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository

class DatabasePathRepository(
    database: AppDatabase,
    private val lastCreatedPathIdRepository: ILastCreatedPathIdRepository
) : IPathRepository {

    companion object {
        private val TAG = DatabasePathRepository::class.java.canonicalName
    }

    private val pathsPointsRelationsDao = database.getPathPointsRelationsDao()
    private val pointsDao = database.getPointsDao()
    private val pathsDao = database.getPathsDao()
    private val pathSegmentsDao = database.getPathSegmentRelationsDao()

    override suspend fun createNewPath(
        startPoint: MapPoint,
        isOuterPath: Boolean,
        timestamp: Long
    ): Long {
        insertNewPoint(startPoint, timestamp).let { insertedPointId ->
            pathsDao.insertPaths(
                listOf(
                    EntityPath(
                        startPointId = insertedPointId,
                        length = 0f,
                        mostCommonRating = MostCommonRating.UNKNOWN.ordinal,
                        isOuterPath = isOuterPath
                    )
                )
            ).let { insertedPathsIds ->
                val insertedPathId = insertedPathsIds[0]
                lastCreatedPathIdRepository.setLastCreatedPathId(insertedPathId)
                insertNewPathPointRelation(insertedPathId, insertedPointId)
                Log.i(TAG, "createNewPath pathId:$insertedPathId startPoint:$startPoint")
                return insertedPathId
            }
        }
    }

    override suspend fun createNewPath(
        pathsSegments: List<MapPathSegment>,
        pathLength: Float?,
        mostCommonRating: MostCommonRating?,
        timestamp: Long,
        isOuterPath: Boolean
    ): Long? {
        if (pathsSegments.isEmpty()) return null

        val pathId = createNewPath(pathsSegments[0].startPoint, isOuterPath, timestamp)

        for (segment in pathsSegments) {
            addNewPathPoint(pathId, segment.finishPoint, segment.rating, timestamp)
        }

        if (pathLength != null) {
            updatePathLength(pathId, pathLength)
        }

        if (mostCommonRating != null) {
            updatePathMostCommonRating(pathId, mostCommonRating)
        }
        return pathId
    }

    override suspend fun addNewPathPoint(
        pathId: Long,
        point: MapPoint,
        segmentRating: SegmentRating,
        timestamp: Long
    ): Long {
        insertNewPoint(point, timestamp).let { insertedPointId ->
            Log.i(TAG, "addNewPathPoint $insertedPointId to pathId $pathId")
            insertNewPathPointRelation(pathId, insertedPointId)
            insertNewPathSegment(pathId, insertedPointId, segmentRating, timestamp)
            return insertedPointId
        }
    }

    private suspend fun insertNewPoint(mapPoint: MapPoint, timestamp: Long): Long {
        return pointsDao.insertPoints(
            listOf(
                EntityPoint(
                    latitude = mapPoint.latitude,
                    longitude = mapPoint.longitude,
                    timestamp = timestamp
                )
            )
        )[0]
    }

    private suspend fun insertNewPathPointRelation(pathId: Long, pointId: Long) {
        pathsPointsRelationsDao.insertPathPointsRelations(
            listOf(
                EntityPathPointRelation(
                    pathId,
                    pointId
                )
            )
        )
    }

    private suspend fun insertNewPathSegment(
        pathId: Long,
        newPointId: Long,
        segmentRating: SegmentRating,
        timestamp: Long
    ) {
        val pathFinishPoint = getPathFinishPoint(pathId)
        if (pathFinishPoint != null) {
            pathSegmentsDao.insertPathSegments(
                listOf(
                    EntityPathSegment(
                        pathId = pathId,
                        startPointId = pathFinishPoint.id,
                        finishPointId = newPointId,
                        rating = segmentRating.ordinal,
                        timestamp = timestamp
                    )
                )
            )
            Log.d(TAG, "insertNewPathSegment with start point id: ${pathFinishPoint.id}")
        } else {
            throw RuntimeException("Trying to add next point to path without start point!")
        }
    }

    private suspend fun getPathFinishPoint(pathId: Long): EntityPoint? {
        var pathFinishPoint: EntityPoint? = pathsDao.getPathStartPoint(pathId)
        var nextPoint: EntityPoint? = pathFinishPoint
        while (nextPoint != null) {
            nextPoint = pathSegmentsDao.getNextPathPoint(nextPoint.id)
            if (nextPoint != null) {
                pathFinishPoint = nextPoint
            }
        }
        return pathFinishPoint
    }

    override suspend fun getAllPathsInfo(): List<EntityPath> {
        return pathsDao.getAllPaths()
    }

    override suspend fun getAllPathPoints(pathId: Long): List<EntityPoint> {
        return pathsPointsRelationsDao.getAllPathPoints(pathId)
    }

    override suspend fun getLastPathInfo(): EntityPath? {
        val pathId = lastCreatedPathIdRepository.getLastCreatedPathId()
        return if (pathId != null) pathsDao.getPathById(pathId) else null
    }

    override suspend fun getLastPathSegments(): List<EntityPathSegment> {
        return ArrayList<EntityPathSegment>().apply {
            val pathId = lastCreatedPathIdRepository.getLastCreatedPathId()
            if (pathId != null) {
                addAll(getAllPathSegments(pathId))
            }
        }
    }

    override suspend fun getPointInfo(pointId: Long): EntityPoint? {
        return pointsDao.getPointById(pointId)
    }

    override suspend fun getAllPathSegments(
        pathId: Long
    ): List<EntityPathSegment> {
        val path = pathsDao.getPathById(pathId)
        return if (path == null) {
            emptyList()
        } else {
            val allPathSegmentsUnsorted = pathSegmentsDao.getAllPathsSegments(pathId)
            if (allPathSegmentsUnsorted.isEmpty()) {
                val legacyPathSegments = getAllPathSegmentsLegacy(path.startPointId)
                coroutineScope {
                    launch {
                        for (segment in legacyPathSegments) {
                            pathSegmentsDao.updatePathSegmentPathId(segment.startPointId, segment.finishPointId, pathId)
                        }
                    }
                }
                Log.d(TAG, "return getAllPathSegments with legacy method")
                return legacyPathSegments
            } else {
                return allPathSegmentsUnsorted.sortedBy { it.timestamp }
            }
        }
    }

    /**
     * Legacy because first db version has not pathId and were very unoptimized
     * Called just once - when pathId did not set yet (only on db update)
     */
    private suspend fun getAllPathSegmentsLegacy(startPointId: Long): List<EntityPathSegment> {
        return ArrayList<EntityPathSegment>().apply {
            var currentPoint = pointsDao.getPointById(startPointId)
            if (currentPoint != null) {
                var nextPoint = pathSegmentsDao.getNextPathPoint(currentPoint.id)
                while (nextPoint != null) {
                    val nextPathSegment =
                        pathSegmentsDao.getPathSegmentByPoints(currentPoint!!.id, nextPoint.id)
                    if (nextPathSegment != null) {
                        add(nextPathSegment)
                    } else {
                        Log.w(
                            TAG,
                            "Path segment with points ids: ${currentPoint.id}, ${nextPoint.id} is null!"
                        )
                    }
                    currentPoint = nextPoint
                    nextPoint = pathSegmentsDao.getNextPathPoint(nextPoint.id)
                }
            }
        }
    }

    override suspend fun getPathStartSegment(pathId: Long): EntityPathSegment? {
        val path = pathsDao.getPathById(pathId)
        return if (path == null) {
            null
        } else {
            val secondPathPoint = pathSegmentsDao.getNextPathPoint(path.startPointId)
            if (secondPathPoint == null) {
                null
            } else {
                pathSegmentsDao.getPathSegmentByPoints(path.startPointId, secondPathPoint.id)
            }
        }
    }

    override suspend fun deletePath(pathId: Long) {
        pathsPointsRelationsDao.getAllPathPointsIds(pathId).forEach { pointId ->
            pointsDao.deletePointById(pointId)
        }
        pathsDao.deletePathById(pathId)
    }

    override suspend fun updatePathLength(pathId: Long, length: Float) {
        pathsDao.updatePathLength(pathId, length)
    }

    override suspend fun updatePathMostCommonRating(
        pathId: Long,
        mostCommonRating: MostCommonRating
    ) {
        pathsDao.updatePathMostCommonRating(pathId, mostCommonRating.ordinal)
    }
}
