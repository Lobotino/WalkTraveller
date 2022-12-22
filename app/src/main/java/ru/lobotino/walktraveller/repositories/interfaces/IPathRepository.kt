package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.database.model.Point
import ru.lobotino.walktraveller.model.MapPoint

interface IPathRepository {

    fun createNewPath(startPoint: MapPoint, pathColor: String, onResult: ((Long) -> Unit)? = null)

    fun addNewPathPoint(pathId: Long, point: MapPoint, onResult: ((Long) -> Unit)? = null)

    fun getAllPathPoints(pathId: Long, onResult: (List<Point>) -> Unit)

    fun deletePath(pathId: Long, onResult: (() -> Unit)? = null)

}