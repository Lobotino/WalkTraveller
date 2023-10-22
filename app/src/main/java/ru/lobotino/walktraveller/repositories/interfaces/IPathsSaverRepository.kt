package ru.lobotino.walktraveller.repositories.interfaces

import ru.lobotino.walktraveller.model.map.MapRatingPath

interface IPathsSaverRepository {

    /**
     * @return saved path file name
     */
    suspend fun saveRatingPath(path: MapRatingPath): String?

    /**
     * @return saved path file name
     */
    suspend fun saveRatingPathList(paths: List<MapRatingPath>): String?

}