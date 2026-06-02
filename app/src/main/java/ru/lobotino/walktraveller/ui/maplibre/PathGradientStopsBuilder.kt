package ru.lobotino.walktraveller.ui.maplibre

import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class GradientStop(val progress: Float, val rating: SegmentRating)

object PathGradientStopsBuilder {

    /**
     * Stops for a MapLibre `line-gradient` along [path], normalized to [0..1] of the
     * path's length. Each junction between two ratings contributes a pair of stops
     * that bracket the junction with a blend window. The window width is adaptive:
     * `max(blendMeters, 0.30 * min(prevRunLen, nextRunLen))` so sparse / optimized
     * paths still get a visible transition rather than a hairline switch.
     */
    fun build(path: MapRatingPath, blendMeters: Float): List<GradientStop> {
        val segments = path.pathSegments
        if (segments.isEmpty()) return emptyList()

        // Deduplicated polyline (points, per-edge ratings). MapRatingPath is expected
        // continuous (start of segment N+1 == finish of segment N). Tolerant of gaps:
        // an implicit bridge edge inherits the next segment's rating.
        val points = ArrayList<MapPoint>(segments.size + 1)
        val ratings = ArrayList<SegmentRating>(segments.size)
        for ((index, seg) in segments.withIndex()) {
            if (index == 0) {
                points.add(seg.startPoint)
            } else if (seg.startPoint != points.last()) {
                points.add(seg.startPoint)
                ratings.add(seg.rating)
            }
            if (seg.finishPoint != points.last()) {
                points.add(seg.finishPoint)
                ratings.add(seg.rating)
            }
        }
        if (ratings.isEmpty()) return emptyList()

        val cum = DoubleArray(points.size)
        for (k in 1 until points.size) {
            cum[k] = cum[k - 1] + haversineMeters(points[k - 1], points[k])
        }
        val totalLen = cum.last()
        if (totalLen <= 0.0) return emptyList()

        val junctions = ArrayList<Int>()
        for (k in 1 until ratings.size) {
            if (ratings[k] != ratings[k - 1]) junctions.add(k)
        }

        val stops = ArrayList<GradientStop>(2 + junctions.size * 2)
        stops.add(GradientStop(0f, ratings.first()))

        for ((i, k) in junctions.withIndex()) {
            val dJ = cum[k]
            val dPrev = if (i == 0) 0.0 else cum[junctions[i - 1]]
            val dNext = if (i == junctions.lastIndex) totalLen else cum[junctions[i + 1]]
            val prevRunLen = dJ - dPrev
            val nextRunLen = dNext - dJ
            val blendActual = max(blendMeters.toDouble(), 0.30 * min(prevRunLen, nextRunLen))
            val halfBlend = blendActual / 2.0
            val halfPrev = min(halfBlend, prevRunLen / 2.0)
            val halfNext = min(halfBlend, nextRunLen / 2.0)

            val before = ((dJ - halfPrev) / totalLen).toFloat().coerceIn(0f, 1f)
            val after = ((dJ + halfNext) / totalLen).toFloat().coerceIn(0f, 1f)
            stops.add(GradientStop(before, ratings[k - 1]))
            stops.add(GradientStop(after, ratings[k]))
        }
        stops.add(GradientStop(1f, ratings.last()))

        return enforceMonotonic(stops)
    }

    /**
     * MapLibre requires strictly increasing stop positions. Nudge ties by a tiny
     * epsilon while preserving the visual ordering (colors before vs after).
     */
    private fun enforceMonotonic(stops: List<GradientStop>): List<GradientStop> {
        val epsilon = 1e-6f
        var last = -1f
        val out = ArrayList<GradientStop>(stops.size)
        for (stop in stops) {
            val next = if (stop.progress > last) stop.progress else (last + epsilon).coerceAtMost(1f)
            out.add(GradientStop(next, stop.rating))
            last = next
        }
        return out
    }
}

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
