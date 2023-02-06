package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IDefaultLocationRepository
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class UserLocationInteractor(
    private val locationUpdatesRepository: ILocationUpdatesRepository,
    private val defaultUserLocationRepository: IDefaultLocationRepository
) : IUserLocationInteractor {


    override fun getCurrentUserLocation(resultLocation: (MapPoint) -> Unit) {
        locationUpdatesRepository.updateLocationNow { location -> resultLocation(location.toMapPoint()) }
    }

    override fun getLastUserLocation(): MapPoint {
        return defaultUserLocationRepository.getDefaultUserLocation()
    }
}