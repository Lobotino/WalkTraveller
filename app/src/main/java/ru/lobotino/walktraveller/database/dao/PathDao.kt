package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.PathPointRelation
import ru.lobotino.walktraveller.model.MapPoint

@Dao
interface PathDao {
    @Query("SELECT id_path FROM path_point_relations")
    fun getAllPathsIds(): List<Long>

    @Query("SELECT * FROM path_point_relations")
    fun getAllPathPointRelations(): List<PathPointRelation>

    @Query("SELECT latitude, longitude FROM points, path_point_relations WHERE path_point_relations.id_path = :pathId AND points.id = path_point_relations.id_point")
    fun getPathPointsById(pathId: Long): List<MapPoint>

    @Insert
    fun insertPathPoints(pathPointRelations: List<PathPointRelation>)

    @Query("DELETE FROM path_point_relations WHERE id_path = :pathId")
    fun deletePathById(pathId: Long)

    @Query("DELETE FROM path_point_relations")
    fun deleteAllPathPoints()
}