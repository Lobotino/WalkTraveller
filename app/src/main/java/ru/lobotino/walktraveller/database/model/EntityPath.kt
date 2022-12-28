package ru.lobotino.walktraveller.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paths")
data class EntityPath(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_point_id") val startPointId: Long,
    val color: String,
)