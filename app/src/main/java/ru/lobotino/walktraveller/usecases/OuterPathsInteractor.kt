package ru.lobotino.walktraveller.usecases

import android.net.Uri
import java.util.Date
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.IPathDistancesRepository
import ru.lobotino.walktraveller.repositories.interfaces.IPathsLoaderRepository
import ru.lobotino.walktraveller.usecases.interfaces.IOuterPathsInteractor

class OuterPathsInteractor(
    private val pathsLoaderRepository: IPathsLoaderRepository,
    private val pathDistancesRepository: IPathDistancesRepository
) : IOuterPathsInteractor {

    private val cachedOuterPaths = ArrayList<OuterMapPathInfo>()

    override suspend fun getAllPaths(pathsUri: Uri): List<MapPathInfo> {
        val todayDate = Date().time //fixme save timestamp on share
        val paths = pathsLoaderRepository.loadAllRatingPathsFromFile(pathsUri)

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