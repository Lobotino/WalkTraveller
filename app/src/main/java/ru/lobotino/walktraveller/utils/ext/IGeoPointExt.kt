package ru.lobotino.walktraveller.utils.ext

import org.osmdroid.api.IGeoPoint
import ru.lobotino.walktraveller.model.map.MapPoint

fun IGeoPoint.toMapPoint(): MapPoint {
    return MapPoint(latitude, longitude)
}
