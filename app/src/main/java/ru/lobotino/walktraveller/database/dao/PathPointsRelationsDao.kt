package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.PathPointRelation

@Dao
interface PathPointsRelationsDao {

    @Query("SELECT * FROM path_point_relations")
    fun getAllPathPointRelations(): List<PathPointRelation>

    @Query("SELECT id_point FROM path_point_relations WHERE id_path = :pathId")
    fun getAllPathPointsIds(pathId: Long): List<Long>

    @Insert
    fun insertPathPointsRelations(pathPointRelations: List<PathPointRelation>)

}