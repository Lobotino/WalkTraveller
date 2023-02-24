package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapRatingPath

interface IMapPathsInteractor {

    suspend fun getAllSavedCommonPaths(): List<MapCommonPath>

    suspend fun getSavedCommonPath(pathId: Long): MapCommonPath?

    suspend fun getLastSavedRatingPath(): MapRatingPath?

    suspend fun getAllSavedRatingPaths(): List<MapRatingPath>

    suspend fun getAllSavedPathsInfo(): List<MapPathInfo>

    suspend fun getSavedRatingPath(pathId: Long): MapRatingPath?

    suspend fun deletePath(pathId: Long)

}