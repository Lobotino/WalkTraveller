package ru.lobotino.walktraveller.ui.maplibre

import androidx.annotation.ColorInt
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath

class MapLibrePathController(
    private val ratingColors: Map<SegmentRating, Int>,
    @ColorInt private val commonPathColor: Int,
    private val lineWidthDp: Float = 6f,
) {
    private var style: Style? = null

    private val savedRatingFeatures = LinkedHashMap<Long, List<Feature>>()
    private val commonFeatures = LinkedHashMap<Long, Feature>()
    private val currentSegments = ArrayList<MapPathSegment>()

    fun onStyleLoaded(style: Style) {
        this.style = style

        style.addSource(GeoJsonSource(SOURCE_SAVED_RATING))
        style.addSource(GeoJsonSource(SOURCE_SAVED_COMMON))
        style.addSource(GeoJsonSource(SOURCE_CURRENT))

        style.addLayer(ratingLineLayer(LAYER_SAVED_RATING, SOURCE_SAVED_RATING))
        style.addLayer(commonLineLayer(LAYER_SAVED_COMMON, SOURCE_SAVED_COMMON))
        style.addLayer(ratingLineLayer(LAYER_CURRENT, SOURCE_CURRENT))

        pushSavedRating()
        pushCommon()
        pushCurrent()
    }

    fun showRatingPaths(paths: List<MapRatingPath>) {
        for (path in paths) {
            savedRatingFeatures[path.pathId] = PathGeoJsonMapper.ratingPathToFeatures(path)
        }
        pushSavedRating()
    }

    fun showCommonPaths(paths: List<MapCommonPath>) {
        for (path in paths) {
            commonFeatures[path.pathId] = PathGeoJsonMapper.commonPathToFeature(path)
        }
        pushCommon()
    }

    fun appendCurrentPathSegments(segments: List<MapPathSegment>) {
        currentSegments.addAll(segments)
        pushCurrent()
    }

    /** Stop accumulating into the current run; leave the drawn line until clear(). */
    fun finishCurrentPath() {
        currentSegments.clear()
    }

    fun hidePath(pathId: Long) {
        val removed = (savedRatingFeatures.remove(pathId) != null) or
            (commonFeatures.remove(pathId) != null)
        if (removed) {
            pushSavedRating()
            pushCommon()
        }
    }

    fun hidePaths(pathIds: List<Long>) {
        var removed = false
        for (id in pathIds) {
            if (savedRatingFeatures.remove(id) != null) removed = true
            if (commonFeatures.remove(id) != null) removed = true
        }
        if (removed) {
            pushSavedRating()
            pushCommon()
        }
    }

    fun clear() {
        savedRatingFeatures.clear()
        commonFeatures.clear()
        currentSegments.clear()
        pushSavedRating()
        pushCommon()
        pushCurrent()
    }

    private fun pushSavedRating() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_SAVED_RATING)
            ?.setGeoJson(FeatureCollection.fromFeatures(savedRatingFeatures.values.flatten()))
    }

    private fun pushCommon() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_SAVED_COMMON)
            ?.setGeoJson(FeatureCollection.fromFeatures(commonFeatures.values.toList()))
    }

    private fun pushCurrent() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_CURRENT)
            ?.setGeoJson(
                FeatureCollection.fromFeatures(
                    PathGeoJsonMapper.segmentsToFeatures(CURRENT_PATH_ID, currentSegments)
                )
            )
    }

    private fun ratingLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(ratingColorExpression()),
            PropertyFactory.lineWidth(lineWidthDp),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    private fun commonLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(commonPathColor),
            PropertyFactory.lineWidth(lineWidthDp),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    private fun ratingColorExpression(): Expression =
        Expression.match(
            Expression.get(PathGeoJsonMapper.PROPERTY_RATING),
            Expression.literal(SegmentRating.PERFECT.name), Expression.color(color(SegmentRating.PERFECT)),
            Expression.literal(SegmentRating.GOOD.name), Expression.color(color(SegmentRating.GOOD)),
            Expression.literal(SegmentRating.NORMAL.name), Expression.color(color(SegmentRating.NORMAL)),
            Expression.literal(SegmentRating.BADLY.name), Expression.color(color(SegmentRating.BADLY)),
            Expression.color(color(SegmentRating.NONE)),
        )

    @ColorInt
    private fun color(rating: SegmentRating): Int =
        ratingColors[rating] ?: ratingColors.getValue(SegmentRating.NONE)

    companion object {
        private const val CURRENT_PATH_ID = -1L
        private const val SOURCE_SAVED_RATING = "wt-saved-rating-source"
        private const val SOURCE_SAVED_COMMON = "wt-saved-common-source"
        private const val SOURCE_CURRENT = "wt-current-source"
        private const val LAYER_SAVED_RATING = "wt-saved-rating-layer"
        private const val LAYER_SAVED_COMMON = "wt-saved-common-layer"
        private const val LAYER_CURRENT = "wt-current-layer"
    }
}
