package ru.lobotino.walktraveller.repositories

import android.content.SharedPreferences
import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPathPointRelation
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository

class LocalPathRepository(
    database: AppDatabase,
    private val sharedPreferences: SharedPreferences
) : IPathRepository {

    companion object {
        private const val KEY_LAST_PATH_ID = "last_path_id"
    }

    private val pathsPointsRelationsDao = database.getPathPointsRelationsDao()
    private val pointsDao = database.getPointsDao()
    private val pathsDao = database.getPathsDao()
    private val pathSegmentsDao = database.getPathSegmentRelationsDao()

    override suspend fun createNewPath(
        startPoint: MapPoint,
        pathColor: String
    ): Long {
        insertNewPoint(startPoint).let { insertedPointId ->
            pathsDao.insertPaths(
                listOf(
                    EntityPath(
                        startPointId = insertedPointId,
                        color = pathColor
                    )
                )
            ).let { insertedPathsIds ->
                val insertedPathId = insertedPathsIds[0]
                setLastPathId(insertedPathId)
                insertNewPathPointRelation(insertedPathId, insertedPointId)
                return insertedPathId
            }
        }
    }

    override suspend fun addNewPathPoint(pathId: Long, point: MapPoint): Long {
        insertNewPoint(point).let { insertedPointId ->
            insertNewPathPointRelation(pathId, insertedPointId)
            insertNewPathSegment(pathId, insertedPointId)
            return insertedPointId
        }
    }

    private suspend fun insertNewPoint(mapPoint: MapPoint): Long {
        return pointsDao.insertPoints(
            listOf(
                EntityPoint(
                    latitude = mapPoint.latitude,
                    longitude = mapPoint.longitude
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

    private suspend fun insertNewPathSegment(pathId: Long, newPointId: Long) {
        var finishPoint: EntityPoint? = pathsDao.getPathStartPoint(pathId)
        var nextPoint: EntityPoint? = finishPoint
        while (nextPoint != null) {
            nextPoint = pathSegmentsDao.getNextPathPoint(nextPoint.id)
            if (nextPoint != null) {
                finishPoint = nextPoint
            }
        }
        if (finishPoint != null) {
            pathSegmentsDao.insertPathSegments(
                listOf(
                    EntityPathSegment(
                        finishPoint.id,
                        newPointId
                    )
                )
            )
        } else {
            throw RuntimeException("Trying to add next point to path without start point!")
        }
    }

    override suspend fun getAllPaths(): List<EntityPath> {
        return pathsDao.getAllPaths()
    }

    override suspend fun getAllPathPoints(pathId: Long): List<EntityPoint> {
        return ArrayList<EntityPoint>().apply {
            var nextPoint: EntityPoint? = pathsDao.getPathStartPoint(pathId)
            while (nextPoint != null) {
                add(nextPoint)
                nextPoint = pathSegmentsDao.getNextPathPoint(nextPoint.id)
            }
        }
    }

    override suspend fun getLastPathInfo(): EntityPath? {
        val pathId = getLastPathId()
        return if (pathId != null) pathsDao.getPathById(pathId) else null
    }

    override suspend fun getLastPathPoints(): List<EntityPoint> {
        return ArrayList<EntityPoint>().apply {
            val pathId = getLastPathId()
            if (pathId != null) {
                var nextPoint: EntityPoint? = pathsDao.getPathStartPoint(pathId)
                while (nextPoint != null) {
                    add(nextPoint)
                    nextPoint = pathSegmentsDao.getNextPathPoint(nextPoint.id)
                }
            }
        }
    }

    override suspend fun getAllPathSegments(
        pathId: Long
    ): List<Pair<EntityPoint, EntityPoint>> {
        val path = pathsDao.getPathById(pathId)
        return if (path == null) {
            emptyList()
        } else {
            return ArrayList<Pair<EntityPoint, EntityPoint>>().apply {
                var currentPoint = pointsDao.getPointById(path.startPointId)
                if (currentPoint != null) {
                    var nextPoint = pathSegmentsDao.getNextPathPoint(currentPoint.id)
                    while (nextPoint != null) {
                        add(Pair(currentPoint!!, nextPoint))
                        currentPoint = nextPoint
                        nextPoint = pathSegmentsDao.getNextPathPoint(nextPoint.id)
                    }
                }
            }
        }
    }

    override suspend fun deletePath(pathId: Long) {
        pathsPointsRelationsDao.getAllPathPointsIds(pathId).forEach { pointId ->
            pointsDao.deletePointById(pointId)
        }
        pathsDao.deletePathById(pathId)
    }

    private fun getLastPathId(): Long? {
        val lastPathId = sharedPreferences.getLong(KEY_LAST_PATH_ID, -1L)
        return if (lastPathId == -1L) null else lastPathId
    }

    private fun setLastPathId(pathId: Long) {
        sharedPreferences.edit().apply {
            putLong(KEY_LAST_PATH_ID, pathId)
            apply()
        }
    }
}