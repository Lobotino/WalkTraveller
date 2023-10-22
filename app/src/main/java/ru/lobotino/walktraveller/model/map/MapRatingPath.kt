package ru.lobotino.walktraveller.model.map

data class MapRatingPath(
    val pathId: Long,
    val pathSegments: List<MapPathSegment>
)
