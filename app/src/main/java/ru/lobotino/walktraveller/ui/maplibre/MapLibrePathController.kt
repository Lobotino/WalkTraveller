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
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import ru.lobotino.walktraveller.model.SegmentRating
import ru.lobotino.walktraveller.model.map.MapCommonPath
import ru.lobotino.walktraveller.model.map.MapPathSegment
import ru.lobotino.walktraveller.model.map.MapRatingPath

/**
 * Threading: all public mutators (showRatingPaths / hidePath / clear / append /
 * finishCurrentPath / onStyleLoaded) must be called on the Main thread. The
 * controller dispatches heavy feature + gradient-stops work to Dispatchers.Default
 * internally and resumes on Main to touch the MapLibre Style.
 */
class MapLibrePathController(
    private val ratingColors: Map<SegmentRating, Int>,
    @ColorInt private val commonPathColor: Int,
    // Scope whose dispatcher is the UI/Main thread; used to launch off-main
    // builder jobs and resume on Main for Style mutations.
    private val scope: CoroutineScope,
    // MapLibre line-width is in density-independent screen pixels, not raw px.
    private val lineWidth: Float = 6f,
    // Minimum length over which two adjacent rating colors blend at a junction.
    // The actual blend may grow up to 30% of the shorter adjacent run so the
    // transition stays visible on sparse / optimized paths.
    private val blendMeters: Float = 12f,
) {
    private var style: Style? = null

    private val savedRatingPaths = LinkedHashMap<Long, MapRatingPath>()
    private val installedRatingIds = LinkedHashSet<Long>()
    private val commonFeatures = LinkedHashMap<Long, Feature>()
    private val currentSegments = ArrayList<MapPathSegment>()

    private var ratingPushJob: Job? = null
    private var currentPushJob: Job? = null

    fun onStyleLoaded(style: Style) {
        this.style = style
        installedRatingIds.clear()

        style.addSource(GeoJsonSource(SOURCE_SAVED_COMMON))
        style.addSource(GeoJsonSource(SOURCE_CURRENT, GeoJsonOptions().withLineMetrics(true)))

        // Order: per-path rating layers (added below LAYER_SAVED_COMMON) <
        // common layer < current layer. Current sits on top so the active
        // recording renders above saved paths.
        style.addLayer(commonLineLayer(LAYER_SAVED_COMMON, SOURCE_SAVED_COMMON))
        style.addLayer(gradientLineLayer(LAYER_CURRENT, SOURCE_CURRENT))

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
     * Stop accumulating into the current run. The rendered features stay drawn
     * (and survive a style reload) until either clear() is called or the next
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

    private data class RatingPayload(val feature: Feature?, val stops: List<GradientStop>)

    private fun pushSavedRating() {
        if (style == null) return
        val snapshot = LinkedHashMap(savedRatingPaths)
        ratingPushJob?.cancel()
        ratingPushJob = scope.launch {
            val payloads = withContext(Dispatchers.Default) {
                buildMap {
                    for ((id, path) in snapshot) {
                        put(
                            id,
                            RatingPayload(
                                feature = PathGeoJsonMapper.ratingPathToFeature(path),
                                stops = PathGradientStopsBuilder.build(path, blendMeters),
                            ),
                        )
                    }
                }
            }
            val style = this@MapLibrePathController.style ?: return@launch
            // Remove layers/sources for paths no longer in the snapshot.
            val toRemove = installedRatingIds - payloads.keys
            for (id in toRemove) {
                style.removeLayer(ratingLayerId(id))
                style.removeSource(ratingSourceId(id))
                installedRatingIds.remove(id)
            }
            // Install or update layers/sources for paths in the snapshot.
            for ((id, payload) in payloads) {
                if (installedRatingIds.contains(id)) {
                    updateRatingLayer(style, id, payload)
                } else {
                    installRatingLayer(style, id, payload)
                    installedRatingIds.add(id)
                }
            }
        }
    }

    private fun pushCommon() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_SAVED_COMMON)
            ?.setGeoJson(FeatureCollection.fromFeatures(commonFeatures.values.toList()))
    }

    private fun pushCurrent() {
        if (style == null) return
        val snapshot = ArrayList(currentSegments)
        currentPushJob?.cancel()
        currentPushJob = scope.launch {
            val payload = withContext(Dispatchers.Default) {
                val path = MapRatingPath(pathId = CURRENT_PATH_ID, pathSegments = snapshot)
                RatingPayload(
                    feature = PathGeoJsonMapper.segmentsToFeature(CURRENT_PATH_ID, snapshot),
                    stops = PathGradientStopsBuilder.build(path, blendMeters),
                )
            }
            val style = this@MapLibrePathController.style ?: return@launch
            style.getSourceAs<GeoJsonSource>(SOURCE_CURRENT)?.setGeoJson(payload.feature.toCollection())
            style.getLayerAs<LineLayer>(LAYER_CURRENT)?.let { applyGradient(it, payload.stops) }
        }
    }

    private fun installRatingLayer(style: Style, pathId: Long, payload: RatingPayload) {
        val sourceId = ratingSourceId(pathId)
        val layerId = ratingLayerId(pathId)
        style.addSource(
            GeoJsonSource(sourceId, payload.feature.toCollection(), GeoJsonOptions().withLineMetrics(true))
        )
        val layer = LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineWidth(lineWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        applyGradient(layer, payload.stops)
        style.addLayerBelow(layer, LAYER_SAVED_COMMON)
    }

    private fun updateRatingLayer(style: Style, pathId: Long, payload: RatingPayload) {
        style.getSourceAs<GeoJsonSource>(ratingSourceId(pathId))
            ?.setGeoJson(payload.feature.toCollection())
        style.getLayerAs<LineLayer>(ratingLayerId(pathId))?.let { applyGradient(it, payload.stops) }
    }

    /**
     * Always set lineGradient — even on paths with no junctions — so a previously
     * set gradient cannot linger and shadow a fallback lineColor. When stops are
     * empty, synthesize a constant-color "gradient" of the path's first rating
     * (or NONE if unavailable).
     */
    private fun applyGradient(layer: LineLayer, stops: List<GradientStop>) {
        val effective = stops.ifEmpty {
            listOf(
                GradientStop(0f, SegmentRating.NONE),
                GradientStop(1f, SegmentRating.NONE),
            )
        }
        layer.setProperties(PropertyFactory.lineGradient(gradientExpression(effective)))
    }

    private fun gradientExpression(stops: List<GradientStop>): Expression {
        val args = ArrayList<Expression>(stops.size * 2)
        for (stop in stops) {
            args.add(Expression.literal(stop.progress))
            args.add(Expression.color(color(stop.rating)))
        }
        return Expression.interpolate(
            Expression.linear(),
            Expression.lineProgress(),
            *args.toTypedArray(),
        )
    }

    private fun gradientLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(color(SegmentRating.NONE)),
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

    @ColorInt
    private fun color(rating: SegmentRating): Int =
        ratingColors[rating] ?: ratingColors.getValue(SegmentRating.NONE)

    private fun ratingSourceId(pathId: Long): String = "$SOURCE_SAVED_RATING_PREFIX$pathId"
    private fun ratingLayerId(pathId: Long): String = "$LAYER_SAVED_RATING_PREFIX$pathId"

    private fun Feature?.toCollection(): FeatureCollection =
        if (this != null) FeatureCollection.fromFeature(this)
        else FeatureCollection.fromFeatures(emptyArray())

    companion object {
        private const val CURRENT_PATH_ID = -1L
        private const val SOURCE_SAVED_RATING_PREFIX = "wt-saved-rating-source-"
        private const val SOURCE_SAVED_COMMON = "wt-saved-common-source"
        private const val SOURCE_CURRENT = "wt-current-source"
        private const val LAYER_SAVED_RATING_PREFIX = "wt-saved-rating-layer-"
        private const val LAYER_SAVED_COMMON = "wt-saved-common-layer"
        private const val LAYER_CURRENT = "wt-current-layer"
    }
}
