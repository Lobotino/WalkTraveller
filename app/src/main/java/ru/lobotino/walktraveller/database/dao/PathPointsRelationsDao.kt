package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.EntityPathPointRelation
import ru.lobotino.walktraveller.database.model.EntityPoint

@Dao
interface PathPointsRelationsDao {

    @Query("SELECT * FROM path_point_relations")
    suspend fun getAllPathPointRelations(): List<EntityPathPointRelation>

    @Query("SELECT id_point FROM path_point_relations WHERE id_path = :pathId")
    suspend fun getAllPathPointsIds(pathId: Long): List<Long>

    @Query("SELECT points.* FROM points INNER JOIN path_point_relations ON points.id = path_point_relations.id_point WHERE path_point_relations.id_path = :pathId")
    suspend fun getAllPathPoints(pathId: Long): List<EntityPoint>

    @Insert
    suspend fun insertPathPointsRelations(pathPointRelations: List<EntityPathPointRelation>)
}
