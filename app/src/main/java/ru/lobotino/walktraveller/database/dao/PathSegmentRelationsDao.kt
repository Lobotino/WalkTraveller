package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint

@Dao
interface PathSegmentRelationsDao {

    @Query("SELECT * FROM path_segments")
    suspend fun getAllPathSegments(): List<EntityPathSegment>

    @Query(
        "SELECT * FROM points, path_segments WHERE path_segments.id_start_point = :pointId and points.id = path_segments.id_finish_point"
    )
    suspend fun getNextPathPoint(pointId: Long): EntityPoint?

    @Query("SELECT * FROM path_segments WHERE id_start_point = :startPointId and id_finish_point = :finishPointId")
    suspend fun getPathSegmentByPoints(startPointId: Long, finishPointId: Long): EntityPathSegment?

    @Insert
    suspend fun insertPathSegments(segments: List<EntityPathSegment>)
}
