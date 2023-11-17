package ru.lobotino.walktraveller.usecases

import android.net.Uri
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathDistancesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathsLoaderRepository
import ru.lobotino.walktraveller.usecases.interfaces.IOuterPathsInteractor

class OuterPathsInteractor(
    private val pathsLoaderRepository: IPathsLoaderRepository,
    private val pathDistancesRepository: IPathDistancesRepository,
    private val pathRepository: IPathRepository
) : IOuterPathsInteractor {

    private val cachedOuterPaths = ArrayList<OuterMapPathInfo>()

    override suspend fun saveCachedPaths() = coroutineScope {
        cachedOuterPaths.let { cachedOuterPaths ->
            for (path in cachedOuterPaths) {
                val savedPathId = withContext(Dispatchers.IO) { pathRepository.createNewPath(path.pathSegments) }
                if (savedPathId != null) {
                    val updatePathLengthAsync = async(Dispatchers.IO) {
                        pathRepository.updatePathLength(savedPathId, path.pathInfo.length)
                    }
                    val updatePathMostCommonRating = async(Dispatchers.IO) {
                        pathRepository.updatePathMostCommonRating(savedPathId, path.pathInfo.mostCommonRating)
                    }
                    updatePathLengthAsync.await()
                    updatePathMostCommonRating.await()
                }
            }
        }
        cachedOuterPaths.clear()
    }

    override suspend fun getAllPaths(pathsUri: Uri): List<MapPathInfo> {
        val todayDate = Date().time //fixme save timestamp on share
        val paths = withContext(Dispatchers.IO) { pathsLoaderRepository.loadAllRatingPathsFromFile(pathsUri) }

        synchronized(cachedOuterPaths) {
            cachedOuterPaths.clear()

            for (index in paths.indices) {
                val pathSegments = paths[index]
                cachedOuterPaths.add(
                    OuterMapPathInfo(
                        MapPathInfo(
                            index.toLong(),
                            todayDate,
                            pathDistancesRepository.calculateMostCommonPathRating(pathSegments.toTypedArray()),
                            pathDistancesRepository.calculatePathLength(pathSegments.toTypedArray())
                        ), pathSegments
                    )
                )
            }

            return cachedOuterPaths.map { it.pathInfo }
        }
    }

    override fun getCachedOuterPaths(): List<MapRatingPath> {
        return cachedOuterPaths.map { MapRatingPath(it.pathInfo.pathId, it.pathSegments) }
    }

    override fun getCachedOuterPath(tempPathId: Int): MapRatingPath? {
        synchronized(cachedOuterPaths) {
            if (tempPathId in cachedOuterPaths.indices) {
                return MapRatingPath(tempPathId.toLong(), cachedOuterPaths[tempPathId].pathSegments)
            } else {
                return null
            }
        }
    }

    override fun clearCachedOuterPaths() {
        cachedOuterPaths.clear()
    }

    override fun removeCachedPath(tempPathId: Int) {
        synchronized(cachedOuterPaths) {
            if (tempPathId in cachedOuterPaths.indices) {
                cachedOuterPaths.removeAt(tempPathId)
            }
        }
    }

    data class OuterMapPathInfo(val pathInfo: MapPathInfo, val pathSegments: List<MapPathSegment>)
}