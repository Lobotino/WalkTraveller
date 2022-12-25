package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.database.model.Path
import ru.lobotino.walktraveller.database.model.Point
import ru.lobotino.walktraveller.model.MapPoint

interface IPathRepository {

    suspend fun createNewPath(startPoint: MapPoint, pathColor: String): Long

    suspend fun addNewPathPoint(pathId: Long, point: MapPoint): Long

    suspend fun getAllPaths(): List<Path>

    suspend fun getAllPathPoints(pathId: Long): List<Point>

    suspend fun getAllPathSegments(pathId: Long): List<Pair<Point, Point>>

    suspend fun deletePath(pathId: Long)

}