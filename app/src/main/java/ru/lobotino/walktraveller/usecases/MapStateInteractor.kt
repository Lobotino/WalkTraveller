package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.map.MapCameraState
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IMapCameraStateRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor

class MapStateInteractor(private val mapCameraStateRepository: IMapCameraStateRepository) :
    IMapStateInteractor {

    companion object {
        const val DEFAULT_ZOOM = 15.0

        // Moscow city coordinates
        private val defaultLastSeenPoint = MapPoint(55.7522200, 37.6155600)
        private val defaultCameraState = MapCameraState(defaultLastSeenPoint, DEFAULT_ZOOM)
    }

    override fun getLastCameraState(): MapCameraState {
        return mapCameraStateRepository.getLastCameraState() ?: defaultCameraState
    }

    override fun setLastCameraState(state: MapCameraState) {
        mapCameraStateRepository.setLastCameraState(state)
    }
}
