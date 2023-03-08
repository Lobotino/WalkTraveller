package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.map.MapCommonPath

interface IPathRedactor {

    suspend fun deletePath(pathId: Long)

    suspend fun updatePathLength(path: MapCommonPath): Float

}