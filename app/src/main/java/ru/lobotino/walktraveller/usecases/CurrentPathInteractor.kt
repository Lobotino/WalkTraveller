package ru.lobotino.walktraveller.usecases

import java.sql.Timestamp
import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.usecases.interfaces.ICurrentPathInteractor

class CurrentPathInteractor(
    private val databasePathRepository: IPathRepository,
    private val pathRatingRepository: IPathRatingRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ICurrentPathInteractor {

    private var currentPathId: Long? = null

    override suspend fun addNewPathPoint(point: MapPoint) {
        val timestamp = Timestamp(Date().time).time
        if (currentPathId != null) {
            withContext(defaultDispatcher) {
                databasePathRepository.addNewPathPoint(
                    currentPathId!!,
                    point,
                    pathRatingRepository.getCurrentRating(),
                    timestamp
                )
            }
        } else {
            currentPathId = withContext(defaultDispatcher) {
                databasePathRepository.createNewPath(
                    point,
                    false,
                    timestamp
                )
            }
        }
    }

    override fun finishCurrentPath() {
        currentPathId = null
    }
}
