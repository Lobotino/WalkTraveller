package ru.lobotino.walktraveller.usecases

import android.location.Location
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.ILocationUpdatesRepository
import ru.lobotino.walktraveller.utils.ext.toMapPoint

class UserLocationInteractor(
    private val locationUpdatesRepository: ILocationUpdatesRepository
) : IUserLocationInteractor {

    override fun getCurrentUserLocation(
        onSuccess: (MapPoint) -> Unit,
        onEmpty: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        locationUpdatesRepository.updateLocationNow(
            { location: Location -> onSuccess(location.toMapPoint()) },
            onEmpty,
            onError
        )
    }

}