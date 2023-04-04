package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.map.MapPoint

interface IUserLocationInteractor {

    fun getCurrentUserLocation(
        onSuccess: (MapPoint) -> Unit,
        onEmpty: () -> Unit,
        onError: (Exception) -> Unit
    )

}