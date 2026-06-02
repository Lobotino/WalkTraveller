package ru.lobotino.walktraveller.ui.maplibre

import ru.lobotino.walktraveller.model.map.MapRatingPath

/**
 * Axis-aligned lat/lng bounding box for a path. Used to cheaply test whether a
 * path falls inside the current map viewport (with a margin) so the controller
 * can skip installing per-path sources/layers for off-screen paths.
 */
data class PathBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
) {
    fun intersects(other: PathBounds): Boolean =
        minLat <= other.maxLat &&
            maxLat >= other.minLat &&
            minLng <= other.maxLng &&
            maxLng >= other.minLng

    fun expand(latMargin: Double, lngMargin: Double): PathBounds =
        PathBounds(
            minLat = minLat - latMargin,
            maxLat = maxLat + latMargin,
            minLng = minLng - lngMargin,
            maxLng = maxLng + lngMargin,
        )
}

fun pathBounds(path: MapRatingPath): PathBounds? {
    val segments = path.pathSegments
    if (segments.isEmpty()) return null
    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLng = Double.POSITIVE_INFINITY
    var maxLng = Double.NEGATIVE_INFINITY
    for (seg in segments) {
        val s = seg.startPoint
        val f = seg.finishPoint
        if (s.latitude < minLat) minLat = s.latitude
        if (s.latitude > maxLat) maxLat = s.latitude
        if (s.longitude < minLng) minLng = s.longitude
        if (s.longitude > maxLng) maxLng = s.longitude
        if (f.latitude < minLat) minLat = f.latitude
        if (f.latitude > maxLat) maxLat = f.latitude
        if (f.longitude < minLng) minLng = f.longitude
        if (f.longitude > maxLng) maxLng = f.longitude
    }
    return PathBounds(minLat = minLat, maxLat = maxLat, minLng = minLng, maxLng = maxLng)
}
