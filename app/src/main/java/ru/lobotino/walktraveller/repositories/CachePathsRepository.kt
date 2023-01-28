package ru.lobotino.walktraveller.repositories

import android.util.ArrayMap
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapRatingPath
import ru.lobotino.walktraveller.repositories.interfaces.ICachePathsRepository

class CachePathsRepository : ICachePathsRepository {

    private val cachedMapRatingPaths = ArrayMap<Long, MapRatingPath>()
    private val cachedMapCommonPaths = ArrayMap<Long, MapCommonPath>()

    override fun saveRatingPath(ratingPath: MapRatingPath) {
        cachedMapRatingPaths[ratingPath.pathId] = ratingPath
    }

    override fun saveCommonPath(commonPath: MapCommonPath) {
        cachedMapCommonPaths[commonPath.pathId] = commonPath
    }

    override fun getRatingPath(pathId: Long): MapRatingPath? {
        return cachedMapRatingPaths[pathId]
    }

    override fun getCommonPath(pathId: Long): MapCommonPath? {
        return cachedMapCommonPaths[pathId]
    }
}