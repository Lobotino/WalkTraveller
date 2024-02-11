package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.EntityPath
import ru.lobotino.walktraveller.database.model.EntityPoint

@Dao
interface PathsDao {

    @Query("SELECT * FROM paths")
    suspend fun getAllPaths(): List<EntityPath>

    @Query("SELECT * FROM paths WHERE id = :pathId")
    suspend fun getPathById(pathId: Long): EntityPath?

    @Query(
        "SELECT points.id, latitude, longitude, points.timestamp FROM points, paths WHERE paths.id = :pathId and points.id = start_point_id"
    )
    suspend fun getPathStartPoint(pathId: Long): EntityPoint?

    @Insert
    suspend fun insertPaths(paths: List<EntityPath>): List<Long>

    @Query("DELETE FROM paths WHERE id = :pathId")
    suspend fun deletePathById(pathId: Long)

    @Query("UPDATE paths SET length = :length WHERE id = :pathId")
    suspend fun updatePathLength(pathId: Long, length: Float)

    @Query("UPDATE paths SET most_common_rating = :mostCommonRating WHERE id = :pathId")
    suspend fun updatePathMostCommonRating(pathId: Long, mostCommonRating: Int)
}
