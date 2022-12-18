package ru.lobotino.walktraveller.repositories

import ru.lobotino.walktraveller.usecases.IPathInteractor

class PathInteractor : IPathInteractor {

    companion object {
        private const val DEFAULT_CITY_LATITUDE = 55.1540200
        private const val DEFAULT_CITY_LONGITUDE = 61.4291500
    }

    override fun getLastPathFinishPosition(): Pair<Double, Double> {
        return Pair(DEFAULT_CITY_LATITUDE, DEFAULT_CITY_LONGITUDE)
    }
}