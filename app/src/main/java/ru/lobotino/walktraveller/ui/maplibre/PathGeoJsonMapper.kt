package ru.lobotino.walktraveller.ui.maplibre

import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath

object PathGeoJsonMapper {

    const val PROPERTY_PATH_ID = "path_id"

    fun ratingPathToFeature(path: MapRatingPath): Feature? =
        segmentsToFeature(path.pathId, path.pathSegments)

    fun commonPathToFeature(path: MapCommonPath): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(path.pathPoints.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, path.pathId)
        }

    /**
     * Concatenates rating segments into a single deduplicated LineString feature.
     * Color is applied by the layer's `line-gradient` (built separately by
     * [PathGradientStopsBuilder]), not by feature properties — the gradient is GPU
     * per-pixel and stays stable across zoom rather than relying on per-feature
     * sub-edges that the tile tessellator culls at low zoom.
     *
     * Returns null when fewer than two distinct points are available.
     */
    fun segmentsToFeature(pathId: Long, segments: List<MapPathSegment>): Feature? {
        if (segments.isEmpty()) return null
        val points = ArrayList<MapPoint>(segments.size + 1)
        for ((index, seg) in segments.withIndex()) {
            if (index == 0) {
                points.add(seg.startPoint)
            } else if (seg.startPoint != points.last()) {
                points.add(seg.startPoint)
            }
            if (seg.finishPoint != points.last()) {
                points.add(seg.finishPoint)
            }
        }
        if (points.size < 2) return null
        return Feature.fromGeometry(
            LineString.fromLngLats(points.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, pathId)
        }
    }

    private fun MapPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)
}
