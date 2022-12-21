package ru.lobotino.walktraveller.repositories

import ru.lobotino.walktraveller.model.MapPoint

interface IPathRepository {

    fun addNewPathPoints(pathId: Long, points: List<MapPoint>)

    fun getAllPathPoints(pathId: Long): List<MapPoint>

    fun getAllPaths(): List<Long>

}