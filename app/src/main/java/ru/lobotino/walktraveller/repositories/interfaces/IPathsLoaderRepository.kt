package ru.lobotino.walktraveller.repositories.interfaces

import android.net.Uri
import ru.lobotino.walktraveller.model.map.MapPathSegment

interface IPathsLoaderRepository {

    suspend fun loadAllRatingPathsFromFile(fileUri: Uri): List<List<MapPathSegment>>
}
