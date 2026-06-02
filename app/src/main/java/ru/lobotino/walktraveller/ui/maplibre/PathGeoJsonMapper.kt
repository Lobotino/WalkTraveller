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
    ): List<Feature> =
        RatingPathFeatureBuilder.build(path, blendMeters, colorOf)
            .map { it.toFeature(path.pathId) }

    fun segmentsToFeatures(
        pathId: Long,
        segments: List<MapPathSegment>,
        blendMeters: Float,
        colorOf: (SegmentRating) -> Int,
    ): List<Feature> =
        RatingPathFeatureBuilder.build(MapRatingPath(pathId, segments), blendMeters, colorOf)
            .map { it.toFeature(pathId) }

    fun commonPathToFeature(path: MapCommonPath): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(path.pathPoints.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, path.pathId)
        }

    private fun ColoredEdge.toFeature(pathId: Long): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(listOf(start.toPoint(), end.toPoint()))
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, pathId)
            addStringProperty(PROPERTY_COLOR, toHexColorString(color))
        }

    private fun MapPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)

    private fun toHexColorString(@androidx.annotation.ColorInt color: Int): String {
        // MapLibre accepts "#RRGGBB" or "#AARRGGBB". Emit AARRGGBB so alpha is explicit.
        return String.format("#%08X", color)
    }
}
