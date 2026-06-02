package ru.lobotino.walktraveller.ui.maplibre

import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapPoint
import ru.lobotino.walktraveller.model.map.MapRatingPath

object PathGeoJsonMapper {

    const val PROPERTY_PATH_ID = "path_id"
    const val PROPERTY_COLOR = "color"

    fun ratingPathToFeatures(
        path: MapRatingPath,
        blendMeters: Float,
        colorOf: (SegmentRating) -> Int,
        subdivisions: Int = 8,
    ): List<Feature> =
        edgesToFeatures(
            pathId = path.pathId,
            edges = RatingPathFeatureBuilder.build(path, blendMeters, colorOf, subdivisions),
        )

    fun segmentsToFeatures(
        pathId: Long,
        segments: List<MapPathSegment>,
        blendMeters: Float,
        colorOf: (SegmentRating) -> Int,
        subdivisions: Int = 8,
    ): List<Feature> =
        edgesToFeatures(
            pathId = pathId,
            edges = RatingPathFeatureBuilder.build(MapRatingPath(pathId, segments), blendMeters, colorOf, subdivisions),
        )

    fun commonPathToFeature(path: MapCommonPath): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(path.pathPoints.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, path.pathId)
        }

    /**
     * Batch consecutive same-color and contiguous edges into single multi-point
     * LineStrings. Solid runs collapse into one feature; sub-edges in blend regions
     * generally stay one feature each because adjacent colors differ.
     *
     * Keeping solid runs as a single LineString avoids zoom-dependent gaps that
     * appear when adjacent two-point features fail to tessellate flush.
     */
    private fun edgesToFeatures(pathId: Long, edges: List<ColoredEdge>): List<Feature> {
        if (edges.isEmpty()) return emptyList()
        val features = ArrayList<Feature>()
        var currentColor = edges[0].color
        var currentPoints = ArrayList<MapPoint>().apply {
            add(edges[0].start)
            add(edges[0].end)
        }
        for (i in 1 until edges.size) {
            val edge = edges[i]
            if (edge.color == currentColor && edge.start == currentPoints.last()) {
                currentPoints.add(edge.end)
            } else {
                features.add(makeFeature(pathId, currentColor, currentPoints))
                currentColor = edge.color
                currentPoints = ArrayList<MapPoint>().apply {
                    add(edge.start)
                    add(edge.end)
                }
            }
        }
        features.add(makeFeature(pathId, currentColor, currentPoints))
        return features
    }

    private fun makeFeature(pathId: Long, color: Int, points: List<MapPoint>): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(points.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, pathId)
            addStringProperty(PROPERTY_COLOR, toHexColorString(color))
        }

    private fun MapPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)

    /**
     * MapLibre's color parser follows CSS: `#RRGGBB` (or `#RRGGBBAA`), not the
     * `#AARRGGBB` ordering of Android color ints. Rating colors are always opaque,
     * so we drop alpha and emit `#RRGGBB`.
     */
    private fun toHexColorString(@androidx.annotation.ColorInt color: Int): String =
        String.format("#%06X", color and 0xFFFFFF)
}
