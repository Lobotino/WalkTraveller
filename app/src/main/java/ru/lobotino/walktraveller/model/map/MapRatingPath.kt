package ru.lobotino.walktraveller.model.map

data class MapRatingPath(
    val pathId: Long,
    val startPointId: MapPoint,
    val pathSegments: List<MapPathSegment>
)
