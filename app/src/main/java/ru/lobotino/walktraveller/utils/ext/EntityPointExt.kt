package ru.lobotino.walktraveller.utils.ext

import ru.lobotino.walktraveller.database.model.EntityPoint
import ru.lobotino.walktraveller.model.map.MapPoint

fun EntityPoint.toMapPoint(): MapPoint {
    return MapPoint(latitude, longitude)
}