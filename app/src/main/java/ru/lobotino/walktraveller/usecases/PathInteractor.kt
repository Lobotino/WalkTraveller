package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.lobotino.walktraveller.model.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPathInteractor
import kotlin.random.Random

class PathInteractor(
    private val pathRepository: IPathRepository,
    private val pathColorsList: List<String>,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IPathInteractor {

    private var currentPathId: Long? = null

    override fun addNewPathPoint(point: MapPoint) {
        CoroutineScope(defaultDispatcher).launch {
            if (currentPathId != null) {
                pathRepository.addNewPathPoint(currentPathId!!, point)
            } else {
                val newPathColor = pathColorsList[Random.nextInt(0, pathColorsList.size)] //TODO

                currentPathId = pathRepository.createNewPath(
                    point,
                    newPathColor
                )
            }
        }
    }

    override fun finishCurrentPath() {
        currentPathId = null
    }
}