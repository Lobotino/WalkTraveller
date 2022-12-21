package ru.lobotino.walktraveller.repositories

import ru.lobotino.walktraveller.database.AppDatabase
import ru.lobotino.walktraveller.database.model.PathPointRelation
import ru.lobotino.walktraveller.database.model.Point
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository

class LocalPathRepository(database: AppDatabase) : IPathRepository {

    private val pathsDao = database.getPathsDao()
    private val pointsDao = database.getPointsDao()

    override fun addNewPathPoints(pathId: Long, points: List<MapPoint>) {
        pointsDao.insertPoints(points.map { mapPoint ->
            Point(
                latitude = mapPoint.latitude,
                longitude = mapPoint.longitude
            )
        }).let { insertedPointsIds ->
            pathsDao.insertPathPoints(insertedPointsIds.map { pointId ->
                PathPointRelation(
                    pathId,
                    pointId
                )
            })
        }
    }

    override fun getAllPathPoints(pathId: Long): List<MapPoint> {
        return pathsDao.getPathPointsById(pathId)
    }

    override fun getAllPaths(): List<Long> {
        return pathsDao.getAllPathsIds()
    }
}