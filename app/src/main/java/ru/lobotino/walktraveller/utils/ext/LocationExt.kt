package ru.lobotino.walktraveller.utils.ext

import android.location.Location
import ru.lobotino.walktraveller.model.map.MapPoint

fun Location.toMapPoint(): MapPoint {
    return MapPoint(latitude, longitude)
}