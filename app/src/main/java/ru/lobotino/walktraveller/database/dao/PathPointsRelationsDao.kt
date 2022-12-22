package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.PathPointRelation

@Dao
interface PathPointsRelationsDao {

    @Query("SELECT * FROM path_point_relations")
    fun getAllPathPointRelations(): List<PathPointRelation>

    @Insert
    fun insertPathPointsRelations(pathPointRelations: List<PathPointRelation>)

}