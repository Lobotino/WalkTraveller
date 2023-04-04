package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.map.MapPoint

interface IMapStateInteractor {

    fun setLastSeenPoint(point: MapPoint)

    fun getLastSeenPoint(): MapPoint

}