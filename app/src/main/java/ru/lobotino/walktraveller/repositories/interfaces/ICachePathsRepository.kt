package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapRatingPath

interface ICachePathsRepository {

    fun saveRatingPath(ratingPath: MapRatingPath, approximationValue: Float = 1f)

    fun saveCommonPath(commonPath: MapCommonPath, approximationValue: Float = 1f)

    fun savePathInfo(pathInfo: MapPathInfo, approximationValue: Float = 1f)

    fun getRatingPath(pathId: Long, approximationValue: Float = 1f): MapRatingPath?

    fun getCommonPath(pathId: Long, approximationValue: Float = 1f): MapCommonPath?

    fun getMapPathInfo(pathId: Long): MapPathInfo?
}
