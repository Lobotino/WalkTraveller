package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.Path
import ru.lobotino.walktraveller.database.model.Point

@Dao
interface PathsDao {

    @Query("SELECT * FROM paths")
    fun getAllPaths(): List<Path>

    @Query("SELECT * FROM paths WHERE id = :pathId")
    fun getPathById(pathId: Long): Path

    @Query("SELECT points.id, latitude, longitude FROM points, paths WHERE paths.id = :pathId and points.id = start_point_id")
    fun getPathStartPoint(pathId: Long): Point

    @Insert
    fun insertPaths(paths: List<Path>): List<Long>

    @Query("DELETE FROM paths WHERE id = :pathId")
    fun deletePathById(pathId: Long)

}