package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.Path
import ru.lobotino.walktraveller.database.model.Point

@Dao
interface PathsDao {

    @Query("SELECT * FROM paths")
    suspend fun getAllPaths(): List<Path>

    @Query("SELECT * FROM paths WHERE id = :pathId")
    suspend fun getPathById(pathId: Long): Path?

    @Query("SELECT points.id, latitude, longitude FROM points, paths WHERE paths.id = :pathId and points.id = start_point_id")
    suspend fun getPathStartPoint(pathId: Long): Point?

    @Insert
    suspend fun insertPaths(paths: List<Path>): List<Long>

    @Query("DELETE FROM paths WHERE id = :pathId")
    suspend fun deletePathById(pathId: Long)

}