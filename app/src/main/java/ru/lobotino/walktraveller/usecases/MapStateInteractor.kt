package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.LastSeenPointRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapStateInteractor

class MapStateInteractor(private val lastSeenPointRepository: LastSeenPointRepository) :
    IMapStateInteractor {

    companion object {
        //Moscow city coordinates
        private val defaultLastSeenPoint = MapPoint(55.7522200, 37.6155600)
    }

    override fun getLastSeenPoint(): MapPoint {
        return lastSeenPointRepository.getLastSeenPoint() ?: defaultLastSeenPoint
    }

    override fun setLastSeenPoint(point: MapPoint) {
        lastSeenPointRepository.setLastSeenPoint(point)
    }
}