package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapPoint

interface IDefaultLocationRepository {

    fun getDefaultUserLocation(): MapPoint

    fun setDefaultUserLocation(point: MapPoint)

}