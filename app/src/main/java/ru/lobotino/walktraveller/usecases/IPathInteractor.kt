package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.MapPoint

interface IPathInteractor {

    fun addNewPathPoint(point: MapPoint)

    fun finishCurrentPath()

}