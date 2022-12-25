package ru.lobotino.walktraveller.usecases.interfaces

import ru.lobotino.walktraveller.model.MapPath

interface IMapPathsInteractor {

    suspend fun getAllSavedPaths() : List<MapPath>

}