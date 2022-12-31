package ru.lobotino.walktraveller.model

data class RatingPath(
    val pathId: Long,
    val startPointId: Long,
    val pathSegments: List<PathSegment>
)
