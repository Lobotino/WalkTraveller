package ru.lobotino.walktraveller.usecases

import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import kotlin.random.Random

class PathInteractor(
    private val pathRepository: IPathRepository,
    private val pathColorsList: List<String>
) : IPathInteractor {

    private var currentPathId: Long? = null

    override fun addNewPathPoint(point: MapPoint) {
        if (currentPathId != null) {
            pathRepository.addNewPathPoint(currentPathId!!, point)
        } else {
            val newPathColor = pathColorsList[Random.nextInt(0, pathColorsList.size)] //TODO

            pathRepository.createNewPath(
                point,
                newPathColor
            ) { pathId ->
                currentPathId = pathId
            }
        }
    }

    override fun finishCurrentPath() {
        currentPathId = null
    }
}