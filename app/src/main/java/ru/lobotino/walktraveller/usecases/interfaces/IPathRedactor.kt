package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.MostCommonRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapRatingPath

interface IPathRedactor {

    suspend fun deletePath(pathId: Long)

    suspend fun updatePathLength(path: MapCommonPath): Float

    suspend fun updatePathMostCommonRating(path: MapRatingPath): MostCommonRating
}
