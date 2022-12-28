package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MapPoint

interface IPathRepository {

    suspend fun createNewPath(startPoint: MapPoint, pathColor: String): Long

    suspend fun addNewPathPoint(pathId: Long, point: MapPoint): Long

    suspend fun getAllPaths(): List<EntityPath>

    suspend fun getAllPathPoints(pathId: Long): List<EntityPoint>

    suspend fun getAllPathSegments(pathId: Long): List<Pair<EntityPoint, EntityPoint>>

    suspend fun getLastPathInfo(): EntityPath?

    suspend fun getLastPathPoints(): List<EntityPoint>

    suspend fun deletePath(pathId: Long)

}