package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.flow.Flow
import ru.lobotino.walktraveller.model.map.MapPoint

interface IUserLocationInteractor {

    fun startTrackCurrentUserLocation()

    fun stopTrackCurrentUserLocation()

    fun getCurrentUserLocation(resultLocation: (MapPoint) -> Unit)

    fun getLastUserLocation(): MapPoint

    fun observeCurrentUserLocation(): Flow<MapPoint>

    fun observeUserLocationErrors(): Flow<String>

}