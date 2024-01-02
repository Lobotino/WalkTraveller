package ru.lobotino.walktraveller.usecases.interfaces

import android.net.Uri
import ru.lobotino.walktraveller.model.map.MapPathInfo
import ru.lobotino.walktraveller.model.map.MapRatingPath

interface IOuterPathsInteractor {

    suspend fun saveCachedPaths()

    suspend fun getAllPaths(pathsUri: Uri): List<MapPathInfo>

    fun getCachedOuterPaths(): List<MapRatingPath>

    fun getCachedOuterPath(tempPathId: Long): MapRatingPath?

    fun clearCachedOuterPaths()

    fun removeCachedPath(tempPathId: Long)
}
