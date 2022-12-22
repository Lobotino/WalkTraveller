package ru.lobotino.walktraveller.repositories

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    override fun createNewPath(
        startPoint: MapPoint,
        pathColor: String,
        onResult: ((Long) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
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
                    onResult?.invoke(insertedPathId)
                }
            }
        }
    }

    override fun addNewPathPoint(pathId: Long, point: MapPoint, onResult: ((Long) -> Unit)?) {
        CoroutineScope(Dispatchers.IO).launch {
            insertNewPoint(point).let { insertedPointId ->
                insertNewPathPointRelation(pathId, insertedPointId)
                insertNewPathSegment(pathId, insertedPointId)
                onResult?.invoke(insertedPointId)
            }
        }
    }

    private fun insertNewPoint(mapPoint: MapPoint): Long {
        return pointsDao.insertPoints(
            listOf(
                Point(
                    latitude = mapPoint.latitude,
                    longitude = mapPoint.longitude
                )
            )
        )[0]
    }

    private fun insertNewPathPointRelation(pathId: Long, pointId: Long) {
        pathsPointsRelationsDao.insertPathPointsRelations(
            listOf(
                PathPointRelation(
                    pathId,
                    pointId
                )
            )
        )
    }

    private fun insertNewPathSegment(pathId: Long, newPointId: Long) {
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

    override fun getAllPathPoints(pathId: Long, onResult: (List<Point>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            onResult(ArrayList<Point>().apply {
                var nextPoint: Point? = pathsDao.getPathStartPoint(pathId)
                while (nextPoint != null) {
                    add(nextPoint)
                    nextPoint = pathSegmentsDao.getNextPathPoint(nextPoint.id)
                }
            })
        }
    }

    override fun deletePath(pathId: Long, onResult: (() -> Unit)?) {
        CoroutineScope(Dispatchers.IO).launch {
            pathsPointsRelationsDao.getAllPathPointsIds(pathId).forEach { pointId ->
                pointsDao.deletePointById(pointId)
            }
            pathsDao.deletePathById(pathId)
            onResult?.invoke()
        }
    }
}