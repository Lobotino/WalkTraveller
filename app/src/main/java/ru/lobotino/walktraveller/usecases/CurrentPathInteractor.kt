package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.repositories.interfaces.IPathRatingRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.usecases.interfaces.ICurrentPathInteractor
import java.sql.Timestamp
import java.util.Date

class CurrentPathInteractor(
    private val databasePathRepository: IPathRepository,
    private val pathRatingRepository: IPathRatingRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ICurrentPathInteractor {

    private var currentPathId: Long? = null

    override suspend fun addNewPathPoint(point: MapPoint) {
        if (currentPathId != null) {
            withContext(defaultDispatcher) {
                databasePathRepository.addNewPathPoint(
                    currentPathId!!,
                    point,
                    pathRatingRepository.getCurrentRating(),
                    Timestamp(Date().time).time
                )
            }
        } else {
            currentPathId = withContext(defaultDispatcher) {
                databasePathRepository.createNewPath(
                    point,
                    false
                )
            }
        }
    }

    override fun finishCurrentPath() {
        currentPathId = null
    }
}
