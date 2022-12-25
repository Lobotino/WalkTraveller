package ru.lobotino.walktraveller.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.model.MapPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.usecases.interfaces.IMapPathsInteractor

class LocalMapPathsInteractor(
    private val localPathRepository: IPathRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IMapPathsInteractor {

    override suspend fun getAllSavedPaths(): List<MapPath> {
        return coroutineScope {
            ArrayList<MapPath>().apply {
                for (path in withContext(defaultDispatcher) { localPathRepository.getAllPaths() }) {
                    add(
                        MapPath(path,
                            withContext(defaultDispatcher) {
                                localPathRepository.getAllPathPoints(path.id)
                            })
                    )
                }
            }
        }
    }
}
