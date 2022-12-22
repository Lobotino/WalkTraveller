package ru.lobotino.walktraveller.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE

@Entity(
    tableName = "path_point_relations",
    primaryKeys = ["id_path", "id_point"],
    foreignKeys = [ForeignKey(
        entity = Point::class,
        parentColumns = ["id"],
        childColumns = ["id_point"],
        onDelete = CASCADE
    ), ForeignKey(
        entity = Path::class,
        parentColumns = ["id"],
        childColumns = ["id_path"],
        onDelete = CASCADE
    )]
)
data class PathPointRelation(
    @ColumnInfo(name = "id_path") val pathId: Long,
    @ColumnInfo(name = "id_point") val pointId: Long
)

