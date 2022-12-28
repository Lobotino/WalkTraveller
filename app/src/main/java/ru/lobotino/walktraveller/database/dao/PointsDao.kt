package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.MapPoint

@Dao
interface PointsDao {
    @Query("SELECT latitude, longitude FROM points")
    suspend fun getAllPoints(): List<MapPoint>

    @Query("SELECT * FROM points WHERE id = :pointId")
    suspend fun getPointById(pointId: Long): EntityPoint?

    @Insert(onConflict = REPLACE)
    suspend fun insertPoints(points: List<EntityPoint>): List<Long>

    @Query("DELETE FROM points WHERE id = :id")
    suspend fun deletePointById(id: Long)
}