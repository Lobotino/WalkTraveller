package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.map.MapCameraState

interface IMapStateInteractor {

    fun setLastCameraState(state: MapCameraState)

    fun getLastCameraState(): MapCameraState
}
