package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathLengthRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.usecases.interfaces.IPathRedactor

class LocalPathRedactor(
    private val databasePathRepository: IPathRepository,
    private val pathLengthRepository: IPathLengthRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IPathRedactor {

    override suspend fun deletePath(pathId: Long) {
        return coroutineScope {
            withContext(defaultDispatcher) {
                databasePathRepository.deletePath(pathId)
            }
        }
    }

    override suspend fun updatePathLength(path: MapCommonPath): Float {
        return coroutineScope {
            val resultLength =
                pathLengthRepository.calculatePathLength(path.pathPoints)

            withContext(defaultDispatcher) {
                databasePathRepository.updatePathLength(path.pathId, resultLength)
            }
            resultLength
        }
    }
}