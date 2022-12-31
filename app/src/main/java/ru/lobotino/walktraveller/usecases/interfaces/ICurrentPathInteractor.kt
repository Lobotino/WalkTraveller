package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.map.MapPoint

interface ICurrentPathInteractor {

    suspend fun addNewPathPoint(point: MapPoint)

    fun finishCurrentPath()

}