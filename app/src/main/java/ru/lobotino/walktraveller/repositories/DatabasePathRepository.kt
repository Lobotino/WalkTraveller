package ru.lobotino.walktraveller.repositories

import android.util.Log
import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.database.model.EntityMapPathSegment
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathPointRelation
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.interop.PointWithRating
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
        timestamp: Long,
        pathLength: Float,
        mostCommonRating: MostCommonRating
    ): Long {
        insertNewPoint(startPoint, timestamp).let { insertedPointId ->
            pathsDao.insertPaths(
                listOf(
                    EntityPath(
                        startPointId = insertedPointId,
                        length = pathLength,
                        mostCommonRating = mostCommonRating.ordinal,
                        isOuterPath = isOuterPath
                    )
                )
            ).let { insertedPathsIds ->
                val insertedPathId = insertedPathsIds[0]
                lastCreatedPathIdRepository.setLastCreatedPathId(insertedPathId)
                insertNewPathPointRelation(insertedPathId, insertedPointId)
                Log.i(TAG, "createNewPath pathId:$insertedPathId startPointId:$insertedPointId startPoint:$startPoint")
                return insertedPathId
            }
        }
    }

    override suspend fun createOuterNewPath(
        pathsSegments: List<MapPathSegment>,
        timestamp: Long,
        pathLength: Float,
        mostCommonRating: MostCommonRating
    ): Long? {
        if (pathsSegments.isEmpty()) return null

        var pointTimestamp: Long = timestamp

        val pathId = createNewPath(pathsSegments[0].startPoint, true, pointTimestamp, pathLength, mostCommonRating)

        addNewPathPoints(
            pathId,
            pathsSegments.map { segment ->
                pointTimestamp++

                val finishPoint = segment.finishPoint
                PointWithRating(
                    latitude = finishPoint.latitude,
                    longitude = finishPoint.longitude,
                    timestamp = pointTimestamp,
                    rating = segment.rating
                )
            }
        )

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

    override suspend fun addNewPathPoints(
        pathId: Long,
        points: List<PointWithRating>
    ): List<Long> {
        insertNewPoints(points).let { insertedPointsIds ->
            if (points.size != insertedPointsIds.size) throw RuntimeException(
                "Trying to add path segments, but not all points are success inserted!"
            )
            Log.i(TAG, "addNewPathPoints $insertedPointsIds to pathId $pathId")
            insertNewPathPointRelations(pathId, insertedPointsIds)
            insertNewPathSegments(pathId, insertedPointsIds.zip(points))
            return insertedPointsIds
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

    private suspend fun insertNewPoints(points: List<PointWithRating>): List<Long> {
        return pointsDao.insertPoints(
            points.map {
                EntityPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    timestamp = it.timestamp
                )
            }
        )
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

    private suspend fun insertNewPathPointRelations(pathId: Long, pointsIds: List<Long>) {
        pathsPointsRelationsDao.insertPathPointsRelations(pointsIds.map { EntityPathPointRelation(pathId, it) })
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

    private suspend fun insertNewPathSegments(
        pathId: Long,
        newPoints: List<Pair<Long, PointWithRating>>
    ) {
        val pathFinishPoint = getPathFinishPoint(pathId) ?: throw RuntimeException("Trying to add next point to path without start point!")
        var lastSegmentPoint = pathFinishPoint.id

        pathSegmentsDao.insertPathSegments(
            newPoints.map { point ->
                val pointId = point.first
                val pointWithRating = point.second

                EntityPathSegment(
                    pathId = pathId,
                    startPointId = lastSegmentPoint,
                    finishPointId = pointId,
                    rating = pointWithRating.rating.ordinal,
                    timestamp = pointWithRating.timestamp
                ).also {
                    lastSegmentPoint = pointId
                }
            }
        )
        Log.i(
            TAG,
            "insertNewPathSegments with start point id: ${pathFinishPoint.id}. Segments inserted: ${newPoints.size}"
        )
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
        Log.d(TAG, "getPathFinishPoint pathId:$pathId, result:$pathFinishPoint")
        return pathFinishPoint
    }

    override suspend fun getAllPathsInfo(): List<EntityPath> {
        return pathsDao.getAllPaths()
    }

    override suspend fun getAllPathPoints(pathId: Long): List<EntityPoint> {
        return pathsPointsRelationsDao.getAllPathPoints(pathId).sortedBy { it.timestamp }
    }

    override suspend fun getLastPathInfo(): EntityPath? {
        val pathId = lastCreatedPathIdRepository.getLastCreatedPathId()
        return if (pathId != null) pathsDao.getPathById(pathId) else null
    }

    override suspend fun getPointInfo(pointId: Long): EntityPoint? {
        return pointsDao.getPointById(pointId)
    }

    override suspend fun getAllPathSegments(pathId: Long): List<EntityMapPathSegment> {
        return pathSegmentsDao.getAllMapPathsSegments(pathId)
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
        Log.d(TAG, "Start delete path $pathId")
        pointsDao.deletePointsByPathId(pathId)
        pathsDao.deletePathById(pathId)
        Log.d(TAG, "Finish delete path $pathId")
    }

    override suspend fun deletePaths(pathIds: List<Long>) {
        Log.d(TAG, "Start delete paths $pathIds")
        pointsDao.deletePointsByPathIds(pathIds)
        pathsDao.deletePathsByIds(pathIds)
        Log.d(TAG, "Finish delete paths $pathIds")
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
