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
    const val PROPERTY_RATING = "rating"

    fun ratingPathToFeatures(path: MapRatingPath): List<Feature> =
        segmentsToFeatures(path.pathId, path.pathSegments)

    fun commonPathToFeature(path: MapCommonPath): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(path.pathPoints.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, path.pathId)
        }

    /**
     * Groups consecutive segments that share a rating and are connected
     * (previous finish == next start) into a single LineString feature.
     */
    fun segmentsToFeatures(pathId: Long, segments: List<MapPathSegment>): List<Feature> {
        val features = ArrayList<Feature>()
        var runPoints: ArrayList<MapPoint>? = null
        var lastSegment: MapPathSegment? = null

        for (segment in segments) {
            val points = runPoints
            if (points == null || lastSegment == null) {
                runPoints = arrayListOf(segment.startPoint, segment.finishPoint)
            } else if (segment.rating == lastSegment.rating &&
                segment.startPoint == lastSegment.finishPoint
            ) {
                points.add(segment.finishPoint)
            } else {
                features.add(buildFeature(pathId, lastSegment.rating, points))
                runPoints = arrayListOf(segment.startPoint, segment.finishPoint)
            }
            lastSegment = segment
        }

        if (runPoints != null && lastSegment != null && runPoints.size >= 2) {
            features.add(buildFeature(pathId, lastSegment.rating, runPoints))
        }
        return features
    }

    private fun buildFeature(
        pathId: Long,
        rating: SegmentRating,
        points: List<MapPoint>,
    ): Feature =
        Feature.fromGeometry(
            LineString.fromLngLats(points.map { it.toPoint() })
        ).apply {
            addNumberProperty(PROPERTY_PATH_ID, pathId)
            addStringProperty(PROPERTY_RATING, rating.name)
        }

    private fun MapPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)
}
