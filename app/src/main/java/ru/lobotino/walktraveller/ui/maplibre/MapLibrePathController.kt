package ru.lobotino.walktraveller.ui.maplibre

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import androidx.annotation.ColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Projection
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
    private val ratingBoundsCache = HashMap<Long, PathBounds>()
    private val installedRatingIds = LinkedHashSet<Long>()
    private val commonFeatures = LinkedHashMap<Long, Feature>()
    private val savedCommonPaths = LinkedHashMap<Long, MapCommonPath>()
    private val currentSegments = ArrayList<MapPathSegment>()

    private var pendingFocusedPathId: Long? = null
    private var currentFocusedPathId: Long? = null

    // Latest viewport seen via onCameraIdle. Null until first camera-idle event.
    // When null, viewport culling is disabled and all saved rating paths are installed.
    private var lastVisibleBounds: PathBounds? = null

    private var ratingPushJob: Job? = null
    private var currentPushJob: Job? = null

    fun onStyleLoaded(style: Style) {
        this.style = style
        installedRatingIds.clear()

        style.addSource(GeoJsonSource(SOURCE_SAVED_COMMON, ratingSourceOptions(withMetrics = false)))
        style.addSource(GeoJsonSource(SOURCE_CURRENT, ratingSourceOptions(withMetrics = true)))

        // Z-order top→bottom: LAYER_CURRENT > LAYER_FOCUSED_TOP > LAYER_FOCUSED_HALO >
        // LAYER_SAVED_COMMON > per-path rating layers. Focused-top and halo sit just
        // above the saved group so the focused path is visible over other paths.
        style.addLayer(commonLineLayer(LAYER_SAVED_COMMON, SOURCE_SAVED_COMMON))
        style.addLayer(gradientLineLayer(LAYER_CURRENT, SOURCE_CURRENT))
        installFocusedLayers(style)

        pushSavedRating()
        pushCommon()
        pushCurrent()

        val pending = pendingFocusedPathId
        val replay = pending ?: currentFocusedPathId
        if (pending != null) pendingFocusedPathId = null
        if (replay != null) setFocusedPath(replay)
    }

    fun showRatingPaths(paths: List<MapRatingPath>) {
        for (path in paths) {
            savedRatingPaths[path.pathId] = path
            val bounds = pathBounds(path)
            if (bounds != null) ratingBoundsCache[path.pathId] = bounds
            else ratingBoundsCache.remove(path.pathId)
        }
        pushSavedRating()
    }

    /**
     * Notify the controller that the camera has settled at a new viewport.
     * Triggers a re-evaluation of which saved rating paths overlap the visible
     * region (with margin) and installs/removes per-path layers accordingly.
     * Must be called on Main.
     */
    fun onCameraIdle(bounds: PathBounds) {
        if (lastVisibleBounds == bounds) return
        lastVisibleBounds = bounds
        pushSavedRating()
    }

    fun showCommonPaths(paths: List<MapCommonPath>) {
        for (path in paths) {
            commonFeatures[path.pathId] = PathGeoJsonMapper.commonPathToFeature(path)
            savedCommonPaths[path.pathId] = path
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
        if (ratingChanged) ratingBoundsCache.remove(pathId)
        val commonChanged = commonFeatures.remove(pathId) != null
        if (commonChanged) savedCommonPaths.remove(pathId)
        if (ratingChanged) pushSavedRating()
        if (commonChanged) pushCommon()
    }

    fun hidePaths(pathIds: List<Long>) {
        var ratingChanged = false
        var commonChanged = false
        for (id in pathIds) {
            if (savedRatingPaths.remove(id) != null) {
                ratingBoundsCache.remove(id)
                ratingChanged = true
            }
            if (commonFeatures.remove(id) != null) {
                savedCommonPaths.remove(id)
                commonChanged = true
            }
        }
        if (ratingChanged) pushSavedRating()
        if (commonChanged) pushCommon()
    }

    fun clear() {
        savedRatingPaths.clear()
        ratingBoundsCache.clear()
        commonFeatures.clear()
        savedCommonPaths.clear()
        currentSegments.clear()
        style?.getSourceAs<GeoJsonSource>(SOURCE_FOCUSED_HALO)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        currentFocusedPathId = null
        pushSavedRating()
        pushCommon()
        pushCurrent()
    }

    fun setFocusedPath(pathId: Long?) {
        val style = this.style ?: run {
            pendingFocusedPathId = pathId
            return
        }
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_FOCUSED_HALO) ?: return

        if (pathId == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            currentFocusedPathId = null
            return
        }

        val ratingPath = savedRatingPaths[pathId]
        val commonPath = savedCommonPaths[pathId]

        val feature: Feature?
        val gradientExpr: Expression
        when {
            ratingPath != null -> {
                feature = PathGeoJsonMapper.ratingPathToFeature(ratingPath)
                val stops = PathGradientStopsBuilder.build(ratingPath, blendMeters).ifEmpty {
                    listOf(
                        GradientStop(0f, SegmentRating.NONE),
                        GradientStop(1f, SegmentRating.NONE),
                    )
                }
                gradientExpr = gradientExpression(stops)
            }
            commonPath != null -> {
                feature = PathGeoJsonMapper.commonPathToFeature(commonPath)
                gradientExpr = uniformGradientExpression(commonPathColor)
            }
            else -> return  // unknown id — leave source/layer untouched
        }
        if (feature == null) return

        source.setGeoJson(feature)
        style.getLayerAs<LineLayer>(LAYER_FOCUSED_TOP)?.setProperties(
            PropertyFactory.lineGradient(gradientExpr)
        )
        currentFocusedPathId = pathId
    }

    /**
     * Returns the pathId of the saved rating or common path whose rendered polyline
     * is closest to [screenPoint], within [tolerancePx] (screen pixels). Returns null
     * if no candidate is within tolerance or no style is loaded yet.
     *
     * [queryRenderedFeatures] is a function (typically `map::queryRenderedFeatures`
     * with the layer-id list curried in by the caller) that returns the features
     * MapLibre considers intersected within the hit rectangle. The controller knows
     * the candidate layer ids and passes them; the caller supplies the live map
     * reference because it lives on MapLibreMap, not Style.
     *
     * Must be called on Main (touches Style + Projection).
     */
    fun findClosestPathAt(
        screenPoint: PointF,
        tolerancePx: Float,
        projection: Projection,
        queryRenderedFeatures: (hitRect: RectF, layerIds: List<String>) -> List<Feature>,
    ): Long? {
        if (this.style == null) return null

        val candidateLayerIds = buildList {
            for (id in installedRatingIds) add(ratingLayerId(id))
            add(LAYER_SAVED_COMMON)
            add(LAYER_FOCUSED_TOP)
        }
        if (candidateLayerIds.isEmpty()) return null

        val hitRect = RectF(
            screenPoint.x - tolerancePx,
            screenPoint.y - tolerancePx,
            screenPoint.x + tolerancePx,
            screenPoint.y + tolerancePx,
        )

        val featureIds = LinkedHashSet<Long>()
        for (feature in queryRenderedFeatures(hitRect, candidateLayerIds)) {
            val id = feature.getNumberProperty(PathGeoJsonMapper.PROPERTY_PATH_ID)
                ?.toLong() ?: continue
            featureIds.add(id)
        }
        if (featureIds.isEmpty()) return null

        val candidates = ArrayList<PolylineHitTest.Candidate>(featureIds.size)
        for (id in featureIds) {
            val vertices = pathVerticesInScreenSpace(id, projection) ?: continue
            if (vertices.size >= 2) {
                candidates.add(PolylineHitTest.Candidate(pathId = id, vertices = vertices))
            }
        }

        return PolylineHitTest.closestPathIdWithin(
            tap = screenPoint,
            candidates = candidates,
            tolerancePx = tolerancePx,
        )
    }

    private fun pathVerticesInScreenSpace(
        pathId: Long,
        projection: Projection,
    ): List<PointF>? {
        val rating = savedRatingPaths[pathId]
        if (rating != null) {
            val points = ArrayList<PointF>(rating.pathSegments.size + 1)
            for ((index, seg) in rating.pathSegments.withIndex()) {
                val start = projection.toScreenLocation(
                    LatLng(seg.startPoint.latitude, seg.startPoint.longitude)
                )
                if (index == 0) {
                    points.add(start)
                } else if (points.isEmpty() || start != points.last()) {
                    points.add(start)
                }
                val finish = projection.toScreenLocation(
                    LatLng(seg.finishPoint.latitude, seg.finishPoint.longitude)
                )
                if (points.isEmpty() || finish != points.last()) {
                    points.add(finish)
                }
            }
            return points
        }
        val common = savedCommonPaths[pathId] ?: return null
        val points = ArrayList<PointF>(common.pathPoints.size)
        for (p in common.pathPoints) {
            points.add(projection.toScreenLocation(LatLng(p.latitude, p.longitude)))
        }
        return points
    }

    private fun installFocusedLayers(style: Style) {
        if (style.getSource(SOURCE_FOCUSED_HALO) != null) return
        style.addSource(
            GeoJsonSource(
                SOURCE_FOCUSED_HALO,
                FeatureCollection.fromFeatures(emptyArray()),
                ratingSourceOptions(withMetrics = true),
            )
        )
        val haloLayer = LineLayer(LAYER_FOCUSED_HALO, SOURCE_FOCUSED_HALO).withProperties(
            PropertyFactory.lineWidth(lineWidth * 2f),
            PropertyFactory.lineColor(Color.parseColor("#FFFFFF")),
            PropertyFactory.lineOpacity(0.9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        style.addLayerAbove(haloLayer, LAYER_SAVED_COMMON)

        val topLayer = LineLayer(LAYER_FOCUSED_TOP, SOURCE_FOCUSED_HALO).withProperties(
            PropertyFactory.lineWidth(lineWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        style.addLayerAbove(topLayer, LAYER_FOCUSED_HALO)
    }

    private data class RatingPayload(val feature: Feature?, val stops: List<GradientStop>)

    private fun pushSavedRating() {
        if (style == null) return
        val desiredIds = desiredRatingIds()
        val snapshot = LinkedHashMap<Long, MapRatingPath>(desiredIds.size).apply {
            for (id in desiredIds) savedRatingPaths[id]?.let { put(id, it) }
        }
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
            // Remove layers/sources for paths no longer desired (off-screen or removed).
            val toRemove = installedRatingIds - payloads.keys
            for (id in toRemove) {
                style.removeLayer(ratingLayerId(id))
                style.removeSource(ratingSourceId(id))
            }
            installedRatingIds.removeAll(toRemove)
            // Install or update layers/sources for desired paths.
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

    /**
     * IDs of saved rating paths to render right now. If no camera viewport has
     * been reported yet, returns all known paths (safe fallback). Otherwise
     * returns paths whose bounding box overlaps the viewport expanded by
     * [VIEWPORT_MARGIN_FRACTION] of its size on each side — the margin gives
     * the user some pan room before a new layer needs to install.
     */
    private fun desiredRatingIds(): Set<Long> {
        val viewport = lastVisibleBounds ?: return savedRatingPaths.keys.toSet()
        val latMargin = (viewport.maxLat - viewport.minLat) * VIEWPORT_MARGIN_FRACTION
        val lngMargin = (viewport.maxLng - viewport.minLng) * VIEWPORT_MARGIN_FRACTION
        val expanded = viewport.expand(latMargin = latMargin, lngMargin = lngMargin)
        return savedRatingPaths.keys.filterTo(LinkedHashSet(savedRatingPaths.size)) { id ->
            val bounds = ratingBoundsCache[id] ?: return@filterTo false
            bounds.intersects(expanded)
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
            GeoJsonSource(sourceId, payload.feature.toCollection(), ratingSourceOptions(withMetrics = true))
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

    private fun uniformGradientExpression(@ColorInt argb: Int): Expression =
        Expression.interpolate(
            Expression.linear(),
            Expression.lineProgress(),
            Expression.literal(0f), Expression.color(argb),
            Expression.literal(1f), Expression.color(argb),
        )

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

    private fun ratingSourceOptions(withMetrics: Boolean): GeoJsonOptions {
        var opts = GeoJsonOptions().withTolerance(SIMPLIFICATION_TOLERANCE)
        if (withMetrics) opts = opts.withLineMetrics(true)
        return opts
    }

    companion object {
        private const val CURRENT_PATH_ID = -1L
        private const val SOURCE_SAVED_RATING_PREFIX = "wt-saved-rating-source-"
        private const val SOURCE_SAVED_COMMON = "wt-saved-common-source"
        private const val SOURCE_CURRENT = "wt-current-source"
        private const val SOURCE_FOCUSED_HALO = "wt-focused-halo-source"
        private const val LAYER_SAVED_RATING_PREFIX = "wt-saved-rating-layer-"
        private const val LAYER_SAVED_COMMON = "wt-saved-common-layer"
        private const val LAYER_CURRENT = "wt-current-layer"
        private const val LAYER_FOCUSED_HALO = "wt-focused-halo-layer"
        private const val LAYER_FOCUSED_TOP = "wt-focused-top-layer"

        // Viewport expansion ratio for culling (fraction of viewport span on each side).
        // 0.5 means user can pan up to ~half a viewport before an off-screen path
        // needs to install — keeps install/remove churn low during normal gestures.
        private const val VIEWPORT_MARGIN_FRACTION = 0.5

        // Douglas-Peucker simplification tolerance applied to all rating / current /
        // common sources. MapLibre default is 0.375 (tile pixels); 1.0 simplifies more
        // aggressively at low zoom while staying visually identical for typical paths.
        private const val SIMPLIFICATION_TOLERANCE = 1.0f
    }
}
