package ru.lobotino.walktraveller.model.interop

import ru.lobotino.walktraveller.model.SegmentRating

data class PointWithRating(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val rating: SegmentRating
)
