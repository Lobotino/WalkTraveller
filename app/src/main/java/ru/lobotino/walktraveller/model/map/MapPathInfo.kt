package ru.lobotino.walktraveller.model.map

import ru.lobotino.walktraveller.model.MostCommonRating

data class MapPathInfo(
    val pathId: Long,
    val timestamp: Long,
    val mostCommonRating: MostCommonRating,
    val length: Float
)
