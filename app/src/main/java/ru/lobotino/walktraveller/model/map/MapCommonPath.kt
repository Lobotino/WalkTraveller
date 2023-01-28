package ru.lobotino.walktraveller.model.map

data class MapCommonPath(val pathId: Long, val startPoint: MapPoint, val pathPoints: List<MapPoint>)
