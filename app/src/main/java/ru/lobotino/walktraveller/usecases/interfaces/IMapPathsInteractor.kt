package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapRatingPath

interface IMapPathsInteractor {

    suspend fun getAllSavedPathsAsCommon(): List<MapCommonPath>

    suspend fun getSavedCommonPath(pathId: Long): MapCommonPath?

    suspend fun getLastSavedRatingPath(): MapRatingPath?

    suspend fun getAllSavedRatingPaths(withRatingOnly: Boolean): List<MapRatingPath>

    suspend fun getAllSavedPathsInfo(): List<MapPathInfo>

    suspend fun getSavedRatingPath(pathId: Long, withRatingOnly: Boolean): MapRatingPath?

}