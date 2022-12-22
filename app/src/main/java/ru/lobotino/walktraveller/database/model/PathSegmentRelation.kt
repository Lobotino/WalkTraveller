package ru.lobotino.walktraveller.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "path_segment_relations",
    primaryKeys = ["id_start_point", "id_finish_point"],
    foreignKeys = [ForeignKey(
        entity = Point::class,
        parentColumns = ["id"],
        childColumns = ["id_start_point"],
        onDelete = ForeignKey.CASCADE
    ), ForeignKey(
        entity = Point::class,
        parentColumns = ["id"],
        childColumns = ["id_finish_point"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PathSegmentRelation(
    @ColumnInfo(name = "id_start_point") val startPointId: Long,
    @ColumnInfo(name = "id_finish_point") val finishPointId: Long
)