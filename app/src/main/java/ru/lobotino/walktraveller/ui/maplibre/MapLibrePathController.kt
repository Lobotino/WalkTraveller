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
    // MapLibre line-width is in density-independent screen pixels, not raw px.
    private val lineWidth: Float = 6f,
    // Minimum length over which two adjacent rating colors blend at a junction.
    // The actual blend may grow up to 30% of the shorter adjacent run so the
    // transition stays visible on sparse / optimized paths.
    private val blendMeters: Float = 12f,
) {
    private var style: Style? = null

    private val savedRatingFeatures = LinkedHashMap<Long, List<Feature>>()
    private val commonFeatures = LinkedHashMap<Long, Feature>()
    private val currentSegments = ArrayList<MapPathSegment>()
    private val currentFeatures = ArrayList<Feature>()

    private val colorOf: (SegmentRating) -> Int = { rating ->
        ratingColors[rating] ?: ratingColors.getValue(SegmentRating.NONE)
    }

    fun onStyleLoaded(style: Style) {
        this.style = style

        style.addSource(GeoJsonSource(SOURCE_SAVED_RATING))
        style.addSource(GeoJsonSource(SOURCE_SAVED_COMMON))
        style.addSource(GeoJsonSource(SOURCE_CURRENT))

        // current-path layer is added last so the active recording renders above saved paths
        style.addLayer(coloredLineLayer(LAYER_SAVED_RATING, SOURCE_SAVED_RATING))
        style.addLayer(commonLineLayer(LAYER_SAVED_COMMON, SOURCE_SAVED_COMMON))
        style.addLayer(coloredLineLayer(LAYER_CURRENT, SOURCE_CURRENT))

        pushSavedRating()
        pushCommon()
        pushCurrent()
    }

    fun showRatingPaths(paths: List<MapRatingPath>) {
        for (path in paths) {
            savedRatingFeatures[path.pathId] = PathGeoJsonMapper.ratingPathToFeatures(path, blendMeters, colorOf)
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
        currentFeatures.clear()
        currentFeatures.addAll(
            PathGeoJsonMapper.segmentsToFeatures(CURRENT_PATH_ID, currentSegments, blendMeters, colorOf)
        )
        pushCurrent()
    }

    /**
     * Stop accumulating into the current run. The rendered features stay drawn (and
     * survive a style reload) until either clear() is called or the next
     * appendCurrentPathSegments() starts a new recording.
     */
    fun finishCurrentPath() {
        currentSegments.clear()
    }

    fun hidePath(pathId: Long) {
        val ratingChanged = savedRatingFeatures.remove(pathId) != null
        val commonChanged = commonFeatures.remove(pathId) != null
        if (ratingChanged) pushSavedRating()
        if (commonChanged) pushCommon()
    }

    fun hidePaths(pathIds: List<Long>) {
        var ratingChanged = false
        var commonChanged = false
        for (id in pathIds) {
            if (savedRatingFeatures.remove(id) != null) ratingChanged = true
            if (commonFeatures.remove(id) != null) commonChanged = true
        }
        if (ratingChanged) pushSavedRating()
        if (commonChanged) pushCommon()
    }

    fun clear() {
        savedRatingFeatures.clear()
        commonFeatures.clear()
        currentSegments.clear()
        currentFeatures.clear()
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
            ?.setGeoJson(FeatureCollection.fromFeatures(currentFeatures))
    }

    private fun coloredLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(
                Expression.toColor(Expression.get(PathGeoJsonMapper.PROPERTY_COLOR))
            ),
            PropertyFactory.lineWidth(lineWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    private fun commonLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(commonPathColor),
            PropertyFactory.lineWidth(lineWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

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
