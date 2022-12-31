package ru.lobotino.walktraveller.utils.ext

import org.osmdroid.util.GeoPoint
import ru.lobotino.walktraveller.model.map.MapPoint

fun MapPoint.toGeoPoint(): GeoPoint {
    return GeoPoint(latitude, longitude)
}