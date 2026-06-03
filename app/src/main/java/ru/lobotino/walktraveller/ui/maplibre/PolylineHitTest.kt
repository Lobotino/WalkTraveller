package ru.lobotino.walktraveller.ui.maplibre

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Pure screen-space hit-test for picking the polyline closest to a tap point.
 * Inputs are in pixels; the caller converts LatLng → PointF via MapLibre's projection.
 */
object PolylineHitTest {

    data class Candidate(val pathId: Long, val vertices: List<PointF>)

    /**
     * Returns the pathId whose polyline has the smallest perpendicular distance
     * to [tap], provided that distance is ≤ [tolerancePx]. Candidates with fewer
     * than two vertices are skipped (no segment to measure against).
     */
    fun closestPathIdWithin(
        tap: PointF,
        candidates: List<Candidate>,
        tolerancePx: Float,
    ): Long? {
        var bestId: Long? = null
        var bestDist = Float.MAX_VALUE
        for (candidate in candidates) {
            val vertices = candidate.vertices
            if (vertices.size < 2) continue
            var dist = Float.MAX_VALUE
            for (i in 0 until vertices.size - 1) {
                val d = distancePointToSegment(tap, vertices[i], vertices[i + 1])
                if (d < dist) dist = d
            }
            if (dist < bestDist) {
                bestDist = dist
                bestId = candidate.pathId
            }
        }
        return if (bestDist <= tolerancePx) bestId else null
    }

    private fun distancePointToSegment(p: PointF, a: PointF, b: PointF): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lenSq = abx * abx + aby * aby
        if (lenSq == 0f) {
            val dx = p.x - a.x
            val dy = p.y - a.y
            return sqrt(dx * dx + dy * dy)
        }
        val t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq
        val tc = t.coerceIn(0f, 1f)
        val cx = a.x + tc * abx
        val cy = a.y + tc * aby
        val dx = p.x - cx
        val dy = p.y - cy
        return sqrt(dx * dx + dy * dy)
    }
}
