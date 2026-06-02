package ru.lobotino.walktraveller.ui.maplibre

import androidx.annotation.ColorInt
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class ColoredEdge(val start: MapPoint, val end: MapPoint, @ColorInt val color: Int)

object RatingPathFeatureBuilder {

    fun build(
        path: MapRatingPath,
        blendMeters: Float,
        colorOf: (SegmentRating) -> Int,
        subdivisions: Int = 8,
    ): List<ColoredEdge> {
        val segments = path.pathSegments
        if (segments.isEmpty()) return emptyList()

        // Deduplicated polyline (points, per-edge ratings). MapRatingPath is expected
        // to be continuous (start of segment N+1 == finish of segment N). For tolerant
        // input where it isn't, the implicit gap edge inherits the next segment's
        // rating and lengthens that run for the adaptive-blend calculation; visually
        // it draws as a straight bridge in that rating's color.
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

        // Junction indices in `ratings` (1..N-1).
        val junctions = ArrayList<Int>()
        for (k in 1 until ratings.size) {
            if (ratings[k] != ratings[k - 1]) junctions.add(k)
        }

        // Adaptive blend params per junction.
        val blendStartDist = DoubleArray(junctions.size)
        val blendEndDist = DoubleArray(junctions.size)
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
            blendStartDist[i] = dJ - halfPrev
            blendEndDist[i] = dJ + halfNext
        }

        val out = ArrayList<ColoredEdge>(ratings.size + junctions.size * subdivisions)

        for (i in 1 until points.size) {
            val edgeStartDist = cum[i - 1]
            val edgeEndDist = cum[i]
            val rating = ratings[i - 1]
            val edgeStartPt = points[i - 1]
            val edgeEndPt = points[i]

            // Detect overlap with any blend region.
            var intersects = false
            for (idx in junctions.indices) {
                if (edgeEndDist > blendStartDist[idx] && edgeStartDist < blendEndDist[idx]) {
                    intersects = true
                    break
                }
            }

            if (!intersects) {
                out.add(ColoredEdge(edgeStartPt, edgeEndPt, colorOf(rating)))
                continue
            }

            for (s in 0 until subdivisions) {
                val t0 = s / subdivisions.toFloat()
                val t1 = (s + 1) / subdivisions.toFloat()
                val subStart = lerpPoint(edgeStartPt, edgeEndPt, t0)
                val subEnd = lerpPoint(edgeStartPt, edgeEndPt, t1)
                val subMidT = (t0 + t1) / 2f
                val subMidDist = edgeStartDist + (edgeEndDist - edgeStartDist) * subMidT
                val color = colorAtDistance(
                    distance = subMidDist,
                    ratings = ratings,
                    cum = cum,
                    junctions = junctions,
                    blendStartDist = blendStartDist,
                    blendEndDist = blendEndDist,
                    colorOf = colorOf,
                )
                out.add(ColoredEdge(subStart, subEnd, color))
            }
        }

        return out
    }

    private fun colorAtDistance(
        distance: Double,
        ratings: List<SegmentRating>,
        cum: DoubleArray,
        junctions: List<Int>,
        blendStartDist: DoubleArray,
        blendEndDist: DoubleArray,
        colorOf: (SegmentRating) -> Int,
    ): Int {
        for (idx in junctions.indices) {
            val start = blendStartDist[idx]
            val end = blendEndDist[idx]
            if (distance in start..end) {
                val k = junctions[idx]
                val tt = if (end > start) ((distance - start) / (end - start)).toFloat() else 0f
                return lerpColor(colorOf(ratings[k - 1]), colorOf(ratings[k]), tt)
            }
        }
        // Outside all blend regions — find which edge contains `distance` and use its rating.
        for (i in 1 until cum.size) {
            if (distance <= cum[i]) return colorOf(ratings[i - 1])
        }
        return colorOf(ratings.last())
    }

    private fun lerpPoint(a: MapPoint, b: MapPoint, t: Float): MapPoint {
        val tt = t.toDouble()
        return MapPoint(
            latitude = a.latitude + (b.latitude - a.latitude) * tt,
            longitude = a.longitude + (b.longitude - a.longitude) * tt,
        )
    }
}

internal fun lerpColor(@ColorInt a: Int, @ColorInt b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    val aA = (a ushr 24) and 0xFF
    val rA = (a ushr 16) and 0xFF
    val gA = (a ushr 8) and 0xFF
    val bA = a and 0xFF
    val aB = (b ushr 24) and 0xFF
    val rB = (b ushr 16) and 0xFF
    val gB = (b ushr 8) and 0xFF
    val bB = b and 0xFF
    val aC = (aA + (aB - aA) * tt).toInt()
    val rC = (rA + (rB - rA) * tt).toInt()
    val gC = (gA + (gB - gA) * tt).toInt()
    val bC = (bA + (bB - bA) * tt).toInt()
    return (aC shl 24) or (rC shl 16) or (gC shl 8) or bC
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
