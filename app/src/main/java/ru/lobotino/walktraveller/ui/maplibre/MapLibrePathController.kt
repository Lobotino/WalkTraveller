package ru.lobotino.walktraveller.ui.maplibre

import androidx.annotation.ColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    // Scope whose dispatcher is the UI/Main thread; used to launch off-main
    // feature-building jobs and resume on Main for setGeoJson.
    private val scope: CoroutineScope,
    // MapLibre line-width is in density-independent screen pixels, not raw px.
    private val lineWidth: Float = 6f,
    // Minimum length over which two adjacent rating colors blend at a junction.
    // The actual blend may grow up to 30% of the shorter adjacent run so the
    // transition stays visible on sparse / optimized paths.
    private val blendMeters: Float = 12f,
    // Subdivision-based junction smoothing kicks in only at this zoom level and
    // above. At wider views each path occupies few pixels and gradient detail
    // wouldn't be visible — emitting solid edges there keeps the feature count
    // bounded so MapLibre's native renderer doesn't choke on many paths at once.
    private val subdivisionsZoomThreshold: Float = 13.5f,
    private val maxSubdivisions: Int = 8,
) {
    private var style: Style? = null

    private val savedRatingPaths = LinkedHashMap<Long, MapRatingPath>()
    private val commonFeatures = LinkedHashMap<Long, Feature>()
    private val currentSegments = ArrayList<MapPathSegment>()

    private var ratingPushJob: Job? = null
    private var currentPushJob: Job? = null

    private var lastReportedZoom: Float = subdivisionsZoomThreshold

    private val colorOf: (SegmentRating) -> Int = { rating ->
        ratingColors[rating] ?: ratingColors.getValue(SegmentRating.NONE)
    }

    private val currentSubdivisions: Int
        get() = if (lastReportedZoom >= subdivisionsZoomThreshold) maxSubdivisions else 0

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
            savedRatingPaths[path.pathId] = path
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

    /**
     * Stop accumulating into the current run. The rendered features stay drawn (and
     * survive a style reload) until either clear() is called or the next
     * appendCurrentPathSegments() starts a new recording.
     */
    fun finishCurrentPath() {
        currentSegments.clear()
    }

    fun hidePath(pathId: Long) {
        val ratingChanged = savedRatingPaths.remove(pathId) != null
        val commonChanged = commonFeatures.remove(pathId) != null
        if (ratingChanged) pushSavedRating()
        if (commonChanged) pushCommon()
    }

    fun hidePaths(pathIds: List<Long>) {
        var ratingChanged = false
        var commonChanged = false
        for (id in pathIds) {
            if (savedRatingPaths.remove(id) != null) ratingChanged = true
            if (commonFeatures.remove(id) != null) commonChanged = true
        }
        if (ratingChanged) pushSavedRating()
        if (commonChanged) pushCommon()
    }

    fun clear() {
        savedRatingPaths.clear()
        commonFeatures.clear()
        currentSegments.clear()
        pushSavedRating()
        pushCommon()
        pushCurrent()
    }

    /**
     * Wire this to MapView's camera-idle listener. When the zoom crosses the
     * subdivisions threshold, rating + current features get rebuilt at the new LOD.
     * Zoom changes that don't cross the threshold are a no-op.
     */
    fun onMapZoomChanged(zoom: Float) {
        val prevSubs = currentSubdivisions
        lastReportedZoom = zoom
        if (currentSubdivisions != prevSubs) {
            pushSavedRating()
            pushCurrent()
        }
    }

    private fun pushSavedRating() {
        if (style == null) return
        val subs = currentSubdivisions
        val snapshot = savedRatingPaths.values.toList()
        ratingPushJob?.cancel()
        ratingPushJob = scope.launch {
            val collection = withContext(Dispatchers.Default) {
                FeatureCollection.fromFeatures(
                    snapshot.flatMap {
                        PathGeoJsonMapper.ratingPathToFeatures(it, blendMeters, colorOf, subs)
                    }
                )
            }
            style?.getSourceAs<GeoJsonSource>(SOURCE_SAVED_RATING)?.setGeoJson(collection)
        }
    }

    private fun pushCommon() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_SAVED_COMMON)
            ?.setGeoJson(FeatureCollection.fromFeatures(commonFeatures.values.toList()))
    }

    private fun pushCurrent() {
        if (style == null) return
        val subs = currentSubdivisions
        val snapshot = ArrayList(currentSegments)
        currentPushJob?.cancel()
        currentPushJob = scope.launch {
            val collection = withContext(Dispatchers.Default) {
                FeatureCollection.fromFeatures(
                    PathGeoJsonMapper.segmentsToFeatures(
                        CURRENT_PATH_ID, snapshot, blendMeters, colorOf, subs
                    )
                )
            }
            style?.getSourceAs<GeoJsonSource>(SOURCE_CURRENT)?.setGeoJson(collection)
        }
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
