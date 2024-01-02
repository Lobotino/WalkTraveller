package ru.lobotino.walktraveller.repositories

import android.util.ArrayMap
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.ICachePathsRepository

class CachePathsRepository : ICachePathsRepository {

    private val cachedMapRatingPaths = ArrayMap<Long, MapRatingPath>()
    private val cachedMapCommonPaths = ArrayMap<Long, MapCommonPath>()
    private val cachedMapPathsInfo = ArrayMap<Long, MapPathInfo>()

    override fun saveRatingPath(ratingPath: MapRatingPath) {
        cachedMapRatingPaths[ratingPath.pathId] = ratingPath
    }

    override fun saveCommonPath(commonPath: MapCommonPath) {
        cachedMapCommonPaths[commonPath.pathId] = commonPath
    }

    override fun savePathInfo(pathInfo: MapPathInfo) {
        cachedMapPathsInfo[pathInfo.pathId] = pathInfo
    }

    override fun getRatingPath(pathId: Long): MapRatingPath? {
        return cachedMapRatingPaths[pathId]
    }

    override fun getCommonPath(pathId: Long): MapCommonPath? {
        return cachedMapCommonPaths[pathId]
    }

    override fun getMapPathInfo(pathId: Long): MapPathInfo? {
        return cachedMapPathsInfo[pathId]
    }
}
