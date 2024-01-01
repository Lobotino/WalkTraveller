package ru.lobotino.walktraveller.usecases

import android.net.Uri
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
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

    private val cachedOuterPaths = ConcurrentHashMap<Long, OuterMapPathInfo>()

    override suspend fun saveCachedPaths() = coroutineScope {
        cachedOuterPaths.let { cachedOuterPaths ->
            for (path in cachedOuterPaths.elements()) {
                withContext(Dispatchers.IO) {
                    pathRepository.createNewPath(
                        path.pathSegments,
                        path.pathInfo.length,
                        path.pathInfo.mostCommonRating,
                        path.pathInfo.timestamp,
                        true
                    )
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
                val tempPathId = Random.nextLong()
                cachedOuterPaths[tempPathId] = OuterMapPathInfo(
                    MapPathInfo(
                        tempPathId,
                        todayDate,
                        pathDistancesRepository.calculateMostCommonPathRating(pathSegments.toTypedArray()),
                        pathDistancesRepository.calculatePathLength(pathSegments.toTypedArray()),
                        true
                    ), pathSegments
                )
            }

            return cachedOuterPaths.values.map { it.pathInfo }
        }
    }

    override fun getCachedOuterPaths(): List<MapRatingPath> {
        return cachedOuterPaths.values.map { MapRatingPath(it.pathInfo.pathId, it.pathSegments) }
    }

    override fun getCachedOuterPath(tempPathId: Long): MapRatingPath? {
        val pathSegments = cachedOuterPaths[tempPathId]?.pathSegments
        return if (pathSegments != null) {
            MapRatingPath(tempPathId, pathSegments)
        } else {
            null
        }
    }

    override fun clearCachedOuterPaths() {
        cachedOuterPaths.clear()
    }

    override fun removeCachedPath(tempPathId: Long) {
        cachedOuterPaths.remove(tempPathId)
    }

    data class OuterMapPathInfo(val pathInfo: MapPathInfo, val pathSegments: List<MapPathSegment>)
}