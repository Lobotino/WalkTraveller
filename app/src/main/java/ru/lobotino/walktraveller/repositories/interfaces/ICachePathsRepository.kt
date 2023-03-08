package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapRatingPath

interface ICachePathsRepository {

    fun saveRatingPath(ratingPath: MapRatingPath)

    fun saveCommonPath(commonPath: MapCommonPath)

    fun savePathInfo(pathInfo: MapPathInfo)

    fun getRatingPath(pathId: Long): MapRatingPath?

    fun getCommonPath(pathId: Long): MapCommonPath?

    fun getMapPathInfo(pathId: Long): MapPathInfo?

}