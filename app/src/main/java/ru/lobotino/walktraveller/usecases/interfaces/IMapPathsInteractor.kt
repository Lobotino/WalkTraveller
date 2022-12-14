package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapRatingPath

interface IMapPathsInteractor {

    suspend fun getAllSavedCommonPaths(): List<MapCommonPath>

    suspend fun getLastSavedRatingPath(): MapRatingPath?

    suspend fun getAllSavedRatingPaths(): List<MapRatingPath>

}