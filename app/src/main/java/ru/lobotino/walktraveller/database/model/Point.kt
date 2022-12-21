package ru.lobotino.walktraveller.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "points")
data class Point(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Long,
    val longitude: Long,
)