package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.PathSegmentRelation
import ru.lobotino.walktraveller.database.model.Point

@Dao
interface PathSegmentRelationsDao {

    @Query("SELECT * FROM path_segment_relations")
    fun getAllPathSegments(): List<PathSegmentRelation>

    @Query("SELECT * FROM points, path_segment_relations WHERE id_start_point = :pointId and points.id = id_finish_point")
    fun getNextPathPoint(pointId: Long): Point?

    @Insert
    fun insertPathSegments(segments: List<PathSegmentRelation>)

}