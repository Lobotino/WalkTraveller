package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.Point
import ru.lobotino.walktraveller.model.MapPoint

@Dao
interface PointsDao {
    @Query("SELECT latitude, longitude FROM points")
    fun getAllPoints(): List<MapPoint>

    @Insert(onConflict = REPLACE)
    fun insertPoints(points: List<Point>): List<Long>

    @Query("DELETE FROM points WHERE id = :id")
    fun deletePointById(id: Long)

    @Query("DELETE FROM points")
    fun deleteAllPoints()
}