package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapPoint

interface IPathLengthRepository {

    fun calculatePathLength(allPathPoints: List<MapPoint>): Float

}