package ru.lobotino.walktraveller.database.model

import androidx.room.Embedded

/**
 * @see ru.lobotino.walktraveller.model.map.MapPathSegment
 */
data class EntityMapPathSegment(
    @Embedded(prefix = "start_") val startPoint: EntityPoint,
    @Embedded(prefix = "finish_") val finishPoint: EntityPoint,
    val rating: Int,
    val timestamp: Long
)