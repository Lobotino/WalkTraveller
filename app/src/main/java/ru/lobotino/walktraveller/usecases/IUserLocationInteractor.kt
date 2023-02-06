package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.map.MapPoint

interface IUserLocationInteractor {

    fun getCurrentUserLocation(resultLocation: (MapPoint) -> Unit)

    fun getLastUserLocation(): MapPoint

}