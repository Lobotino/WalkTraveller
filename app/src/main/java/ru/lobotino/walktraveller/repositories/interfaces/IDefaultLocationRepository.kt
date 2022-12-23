package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.MapPoint

interface IDefaultLocationRepository {

    fun getDefaultUserLocation(): MapPoint

    fun setDefaultUserLocation(point: MapPoint)

}