package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IDefaultLocationRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class UserLocationInteractor(
    private val locationUpdatesRepository: ILocationUpdatesRepository,
    private val defaultUserLocationRepository: IDefaultLocationRepository
) : IUserLocationInteractor {

    override fun startTrackUserLocation() {
        locationUpdatesRepository.startLocationUpdates()
    }

    override fun stopTrackUserLocation() {
        locationUpdatesRepository.stopLocationUpdates()
    }

    override fun getCurrentUserLocation(resultLocation: (MapPoint) -> Unit) {
        locationUpdatesRepository.updateLocationNow { location -> resultLocation(location.toMapPoint()) }
    }

    override fun getLastUserLocation(): MapPoint {
        return defaultUserLocationRepository.getDefaultUserLocation()
    }

    override fun observeCurrentUserLocation(): Flow<MapPoint> {
        return locationUpdatesRepository.observeLocationUpdates()
            .map { location -> location.toMapPoint() }
    }

    override fun observeUserLocationErrors(): Flow<String> {
        return locationUpdatesRepository.observeLocationUpdatesErrors()
    }
}