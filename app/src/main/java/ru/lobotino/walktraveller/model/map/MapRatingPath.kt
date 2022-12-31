package ru.lobotino.walktraveller.model.map

data class MapRatingPath(
    val startPointId: MapPoint,
    val pathSegments: List<MapPathSegment>
)
