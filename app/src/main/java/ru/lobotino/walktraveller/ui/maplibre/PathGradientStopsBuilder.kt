package ru.lobotino.walktraveller.ui.maplibre

import ru.lobotino.walktraveller.model.map.MapPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

internal fun haversineMeters(a: MapPoint, b: MapPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).let { it * it } +
        cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
    val c = 2 * atan2(sqrt(h), sqrt(1 - h))
    return EARTH_RADIUS_METERS * c
}
