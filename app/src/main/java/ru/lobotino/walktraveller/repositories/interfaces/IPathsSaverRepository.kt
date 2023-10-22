package ru.lobotino.walktraveller.repositories.interfaces

import android.net.Uri
import ru.lobotino.walktraveller.model.map.MapRatingPath

interface IPathsSaverRepository {

    /**
     * @return saved path file name
     */
    suspend fun saveRatingPath(path: MapRatingPath): Uri

    /**
     * @return saved path file name
     */
    suspend fun saveRatingPathList(paths: List<MapRatingPath>): Uri

}