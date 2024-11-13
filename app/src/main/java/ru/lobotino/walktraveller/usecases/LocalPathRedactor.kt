package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathDistancesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor

class LocalPathRedactor(
    private val databasePathRepository: IPathRepository,
    private val pathLengthRepository: IPathDistancesRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IPathRedactor {

    /**
     * Deleting now paths ids
     */
    private val deletingPathsIds = HashSet<Long>()

    override fun getDeletingNowPathsIds(): Set<Long> {
        return deletingPathsIds.toSet()
    }

    override suspend fun deletePath(pathId: Long) {
        deletingPathsIds.add(pathId)
        return coroutineScope {
            withContext(defaultDispatcher) {
                databasePathRepository.deletePath(pathId)
                deletingPathsIds.remove(pathId)
            }
        }
    }

    override suspend fun deletePaths(pathIds: List<Long>) {
        deletingPathsIds.addAll(pathIds)
        return coroutineScope {
            withContext(defaultDispatcher) {
                databasePathRepository.deletePaths(pathIds)
                deletingPathsIds.removeAll(pathIds.toSet())
            }
        }
    }

    override suspend fun updatePathLength(path: MapCommonPath): Float {
        return coroutineScope {
            val resultLength =
                pathLengthRepository.calculatePathLength(path.pathPoints.toTypedArray())

            withContext(defaultDispatcher) {
                databasePathRepository.updatePathLength(path.pathId, resultLength)
            }
            resultLength
        }
    }

    override suspend fun updatePathMostCommonRating(path: MapRatingPath): MostCommonRating {
        return coroutineScope {
            val resultMostCommonRating =
                pathLengthRepository.calculateMostCommonPathRating(path.pathSegments.toTypedArray())

            withContext(defaultDispatcher) {
                databasePathRepository.updatePathMostCommonRating(
                    path.pathId,
                    resultMostCommonRating
                )
            }
            resultMostCommonRating
        }
    }
}
