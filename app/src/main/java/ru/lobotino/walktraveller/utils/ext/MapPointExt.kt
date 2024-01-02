package ru.lobotino.walktraveller.utils.ext

import org.osmdroid.util.GeoPoint
import ru.lobotino.walktraveller.model.map.CoordinatePoint
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.utils.EARTH_RADIUS_IN_METERS
import kotlin.math.ln
import kotlin.math.tan

fun MapPoint.toGeoPoint(): GeoPoint {
    return GeoPoint(latitude, longitude)
}

/**
 * Web Mercator projection from geo point to x/y coordinates
 */
fun MapPoint.toCoordinatePoint(): CoordinatePoint {
    return CoordinatePoint(
        Math.toRadians(latitude) * EARTH_RADIUS_IN_METERS,
        ln(tan(Math.PI / 4 + Math.toRadians(longitude) / 2)) * EARTH_RADIUS_IN_METERS
    )
}

fun MapPoint.toText(): String {
    return "$latitude,$longitude"
}
