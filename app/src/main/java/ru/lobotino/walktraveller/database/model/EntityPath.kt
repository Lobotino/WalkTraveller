package ru.lobotino.walktraveller.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paths")
data class EntityPath(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_point_id") val startPointId: Long,
    @ColumnInfo(name = "length") val length: Float,
    @ColumnInfo(
        name = "most_common_rating",
        defaultValue = "5"
    ) val mostCommonRating: Int //Default value 5 because it's ordinal of UNKNOWN state in MostCommonRating model
)