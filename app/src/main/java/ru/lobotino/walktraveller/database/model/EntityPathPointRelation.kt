package ru.lobotino.walktraveller.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE

@Entity(
    tableName = "path_point_relations",
    primaryKeys = ["id_path", "id_point"],
    foreignKeys = [ForeignKey(
        entity = EntityPoint::class,
        parentColumns = ["id"],
        childColumns = ["id_point"],
        onDelete = CASCADE
    ), ForeignKey(
        entity = EntityPath::class,
        parentColumns = ["id"],
        childColumns = ["id_path"],
        onDelete = CASCADE
    )]
)
data class EntityPathPointRelation(
    @ColumnInfo(name = "id_path") val pathId: Long,
    @ColumnInfo(name = "id_point") val pointId: Long
)

