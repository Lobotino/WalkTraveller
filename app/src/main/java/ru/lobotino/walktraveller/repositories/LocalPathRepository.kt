package ru.lobotino.walktraveller.repositories

import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.database.model.Path
import ru.lobotino.walktraveller.database.model.PathPointRelation
import ru.lobotino.walktraveller.database.model.PathSegmentRelation
import ru.lobotino.walktraveller.database.model.Point
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository

class LocalPathRepository(database: AppDatabase) : IPathRepository {

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
                    Path(
                        startPointId = insertedPointId,
                        color = pathColor
                    )
                )
            ).let { insertedPathsIds ->
                val insertedPathId = insertedPathsIds[0]
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
                Point(
                    latitude = mapPoint.latitude,
                    longitude = mapPoint.longitude
                )
            )
        )[0]
    }

    private suspend fun insertNewPathPointRelation(pathId: Long, pointId: Long) {
        pathsPointsRelationsDao.insertPathPointsRelations(
            listOf(
                PathPointRelation(
                    pathId,
                    pointId
                )
            )
        )
    }

    private suspend fun insertNewPathSegment(pathId: Long, newPointId: Long) {
        var finishPoint: Point? = pathsDao.getPathStartPoint(pathId)
        var nextPoint: Point? = finishPoint
        while (nextPoint != null) {
            nextPoint = pathSegmentsDao.getNextPathPoint(nextPoint.id)
            if (nextPoint != null) {
                finishPoint = nextPoint
            }
        }
        if (finishPoint != null) {
            pathSegmentsDao.insertPathSegments(
                listOf(
                    PathSegmentRelation(
                        finishPoint.id,
                        newPointId
                    )
                )
            )
        } else {
            throw RuntimeException("Trying to add next point to path without start point!")
        }
    }

    override suspend fun getAllPaths(): List<Path> {
        return pathsDao.getAllPaths()
    }

    override suspend fun getAllPathPoints(pathId: Long): List<Point> {
        return ArrayList<Point>().apply {
            var nextPoint: Point? = pathsDao.getPathStartPoint(pathId)
            while (nextPoint != null) {
                add(nextPoint)
                nextPoint = pathSegmentsDao.getNextPathPoint(nextPoint.id)
            }
        }
    }

    override suspend fun getAllPathSegments(
        pathId: Long
    ): List<Pair<Point, Point>> {
        val path = pathsDao.getPathById(pathId)
        return if (path == null) {
            emptyList()
        } else {
            return ArrayList<Pair<Point, Point>>().apply {
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
}