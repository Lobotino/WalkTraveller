package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint

@Dao
interface PathSegmentRelationsDao {

    @Query("SELECT * FROM path_segment_relations")
    suspend fun getAllPathSegments(): List<EntityPathSegment>

    @Query("SELECT * FROM points, path_segment_relations WHERE id_start_point = :pointId and points.id = id_finish_point")
    suspend fun getNextPathPoint(pointId: Long): EntityPoint?

    @Insert
    suspend fun insertPathSegments(segments: List<EntityPathSegment>)

}