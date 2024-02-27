package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.EntityPoint

@Dao
interface PointsDao {
    @Query("SELECT * FROM points")
    suspend fun getAllPoints(): List<EntityPoint>

    @Query("SELECT * FROM points WHERE id = :pointId")
    suspend fun getPointById(pointId: Long): EntityPoint?

    @Insert(onConflict = REPLACE)
    suspend fun insertPoints(points: List<EntityPoint>): List<Long>

    @Query("DELETE FROM points WHERE id = :id")
    suspend fun deletePointById(id: Long)

    @Query("DELETE FROM points WHERE id IN (SELECT id_point FROM path_point_relations WHERE id_path = :pathId)")
    suspend fun deletePointsByPathId(pathId: Long)
}
