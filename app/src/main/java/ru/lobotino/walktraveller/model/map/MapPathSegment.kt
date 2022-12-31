package ru.lobotino.walktraveller.model.map

import ru.lobotino.walktraveller.model.SegmentRating

data class MapPathSegment(
    val startPoint: MapPoint,
    val finishPoint: MapPoint,
    val rating: SegmentRating
)
