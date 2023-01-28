package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapRatingPath

interface ICachePathsRepository {

    fun saveRatingPath(ratingPath: MapRatingPath)

    fun saveCommonPath(commonPath: MapCommonPath)

    fun getRatingPath(pathId: Long): MapRatingPath?

    fun getCommonPath(pathId: Long): MapCommonPath?

}