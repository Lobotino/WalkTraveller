package ru.lobotino.walktraveller.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.lobotino.walktraveller.database.model.EntityMapPathSegment
import ru.lobotino.walktraveller.database.model.EntityPathSegment
import ru.lobotino.walktraveller.database.model.EntityPoint

@Dao
interface PathSegmentRelationsDao {

    @Query("SELECT * FROM path_segments")
    suspend fun getAllPathSegments(): List<EntityPathSegment>

    @Query(
        "SELECT\n" +
            "    p1.id AS start_id,\n" +
            "    p1.latitude AS start_latitude,\n" +
            "    p1.longitude AS start_longitude,\n" +
            "    p1.timestamp AS start_timestamp,\n" +
            "    p2.id AS finish_id,\n" +
            "    p2.latitude AS finish_latitude,\n" +
            "    p2.longitude AS finish_longitude,\n" +
            "    p2.timestamp AS finish_timestamp,\n" +
            "    path_segments.rating AS rating,\n" +
            "    path_segments.timestamp AS timestamp\n" +
            "FROM\n" +
            "    path_segments\n" +
            "INNER JOIN\n" +
            "    points AS p1 ON path_segments.id_start_point = p1.id\n" +
            "INNER JOIN\n" +
            "    points AS p2 ON path_segments.id_finish_point = p2.id"
    )
    suspend fun getAllMapPathSegments(): List<EntityMapPathSegment>

    @Query(
        "SELECT\n" +
            "    p1.id AS start_id,\n" +
            "    p1.latitude AS start_latitude,\n" +
            "    p1.longitude AS start_longitude,\n" +
            "    p1.timestamp AS start_timestamp,\n" +
            "    p2.id AS finish_id,\n" +
            "    p2.latitude AS finish_latitude,\n" +
            "    p2.longitude AS finish_longitude,\n" +
            "    p2.timestamp AS finish_timestamp,\n" +
            "    path_segments.rating AS rating,\n" +
            "    path_segments.timestamp AS timestamp\n" +
            "FROM\n" +
            "    path_segments\n" +
            "INNER JOIN\n" +
            "    points AS p1 ON path_segments.id_start_point = p1.id\n" +
            "INNER JOIN\n" +
            "    points AS p2 ON path_segments.id_finish_point = p2.id\n" +
            "WHERE\n" +
            "    path_segments.id_path = :pathId;"
    )
    suspend fun getAllMapPathsSegments(pathId: Long): List<EntityMapPathSegment>

    @Query(
        "SELECT * FROM points, path_segments WHERE path_segments.id_start_point = :pointId and points.id = path_segments.id_finish_point"
    )
    suspend fun getNextPathPoint(pointId: Long): EntityPoint?

    @Query("SELECT * FROM path_segments WHERE id_start_point = :startPointId and id_finish_point = :finishPointId")
    suspend fun getPathSegmentByPoints(startPointId: Long, finishPointId: Long): EntityPathSegment?

    @Insert
    suspend fun insertPathSegments(segments: List<EntityPathSegment>)

    @Query("UPDATE path_segments SET id_path = :pathId WHERE id_start_point = :startPointId AND id_finish_point = :finishPointId")
    suspend fun updatePathSegmentPathId(startPointId: Long, finishPointId: Long, pathId: Long)
}
