package ru.lobotino.walktraveller.repositories

import android.util.ArrayMap
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.ICachePathsRepository

class CachePathsRepository : ICachePathsRepository {

    private val cachedMapRatingPaths = ArrayMap<Long, ApproximatedRatingPath>()
    private val cachedMapCommonPaths = ArrayMap<Long, ApproximatedCommonPath>()
    private val cachedMapPathsInfo = ArrayMap<Long, MapPathInfo>()

    override fun saveRatingPath(ratingPath: MapRatingPath, approximationValue: Float) {
        cachedMapRatingPaths[ratingPath.pathId] = ApproximatedRatingPath(ratingPath, approximationValue)
    }

    override fun saveCommonPath(commonPath: MapCommonPath, approximationValue: Float) {
        cachedMapCommonPaths[commonPath.pathId] = ApproximatedCommonPath(commonPath, approximationValue)
    }

    override fun savePathInfo(pathInfo: MapPathInfo, approximationValue: Float) {
        cachedMapPathsInfo[pathInfo.pathId] = pathInfo
    }

    /**
     * Return path only if current approximation value equals to chosen
     */
    override fun getRatingPath(pathId: Long, approximationValue: Float): MapRatingPath? {
        val savedApproximatedPath = cachedMapRatingPaths[pathId] ?: return null

        if (savedApproximatedPath.approximationValue != approximationValue) {
            return null
        }

        return savedApproximatedPath.mapRatingPath
    }

    /**
     * Return path only if current approximation value equals to chosen
     */
    override fun getCommonPath(pathId: Long, approximationValue: Float): MapCommonPath? {
        val savedApproximatedPath = cachedMapCommonPaths[pathId] ?: return null

        if (savedApproximatedPath.approximationValue != approximationValue) {
            return null
        }

        return savedApproximatedPath.mapCommonPath
    }

    override fun getMapPathInfo(pathId: Long): MapPathInfo? {
        return cachedMapPathsInfo[pathId]
    }

    private data class ApproximatedRatingPath(val mapRatingPath: MapRatingPath, val approximationValue: Float)

    private data class ApproximatedCommonPath(val mapCommonPath: MapCommonPath, val approximationValue: Float)
}
