package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapCameraState

interface IMapCameraStateRepository {

    fun setLastCameraState(state: MapCameraState)

    fun getLastCameraState(): MapCameraState?
}
