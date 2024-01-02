package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapPoint

interface IPointsDistanceRepository {

    fun getDistanceBetweenPointsInMeters(from: MapPoint, to: MapPoint): Float
}
