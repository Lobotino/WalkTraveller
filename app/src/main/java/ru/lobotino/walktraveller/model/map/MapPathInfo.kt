package ru.lobotino.walktraveller.model.map

data class MapPathInfo(
    val pathId: Long,
    val timestamp: Long,
    val color: String,
    val length: Float
)
