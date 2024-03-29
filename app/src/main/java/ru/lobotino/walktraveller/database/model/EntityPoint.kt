package ru.lobotino.walktraveller.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "points")
data class EntityPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
