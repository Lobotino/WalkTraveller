package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapPoint

interface ILastSeenPointRepository {

    fun setLastSeenPoint(point: MapPoint)

    fun getLastSeenPoint(): MapPoint?
}
