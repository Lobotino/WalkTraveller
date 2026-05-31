package ru.lobotino.walktraveller.ui.maplibre

import androidx.annotation.ColorInt
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

class MapLibrePathController(
    private val ratingColors: Map<SegmentRating, Int>,
    @ColorInt private val commonPathColor: Int,
    // MapLibre line-width is in density-independent screen pixels, not raw px.
    private val lineWidth: Float = 6f,
    // Length over which two adjacent rating colors blend at a junction.
    private val blendMeters: Float = 12f,
) {
    private var style: Style? = null

    private val savedRatingPaths = LinkedHashMap<Long, MapRatingPath>()
    private val commonFeatures = LinkedHashMap<Long, Feature>()
    private val currentSegments = ArrayList<MapPathSegment>()

    fun onStyleLoaded(style: Style) {
        this.style = style

        style.addSource(GeoJsonSource(SOURCE_SAVED_COMMON))
        style.addSource(GeoJsonSource(SOURCE_CURRENT, GeoJsonOptions().withLineMetrics(true)))

        // Saved rating layers are inserted below the common layer per-path; ordering: saved-rating < saved-common < current.
        style.addLayer(commonLineLayer(LAYER_SAVED_COMMON, SOURCE_SAVED_COMMON))
        style.addLayer(gradientLineLayer(LAYER_CURRENT, SOURCE_CURRENT))

        for ((pathId, path) in savedRatingPaths) {
            installRatingLayer(style, pathId, path)
        }
        pushCommon()
        pushCurrent()
    }

    fun showRatingPaths(paths: List<MapRatingPath>) {
        val style = this.style
        for (path in paths) {
            val existed = savedRatingPaths.put(path.pathId, path) != null
            if (style == null) continue
            if (existed) {
                updateRatingLayer(style, path)
            } else {
                installRatingLayer(style, path.pathId, path)
            }
        }
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
     * Stop accumulating into the current run. The rendered features are kept so the
     * finished path stays drawn (even across a style reload) until clear().
     */
    fun finishCurrentPath() {
        currentSegments.clear()
    }

    fun hidePath(pathId: Long) {
        val ratingRemoved = savedRatingPaths.remove(pathId) != null
        val commonRemoved = commonFeatures.remove(pathId) != null
        if (ratingRemoved) removeRatingLayer(pathId)
        if (commonRemoved) pushCommon()
    }

    fun hidePaths(pathIds: List<Long>) {
        var commonChanged = false
        for (id in pathIds) {
            if (savedRatingPaths.remove(id) != null) removeRatingLayer(id)
            if (commonFeatures.remove(id) != null) commonChanged = true
        }
        if (commonChanged) pushCommon()
    }

    fun clear() {
        val ratingIds = savedRatingPaths.keys.toList()
        savedRatingPaths.clear()
        commonFeatures.clear()
        currentSegments.clear()
        for (id in ratingIds) removeRatingLayer(id)
        pushCommon()
        pushCurrent()
    }

    private fun installRatingLayer(style: Style, pathId: Long, path: MapRatingPath) {
        val sourceId = ratingSourceId(pathId)
        val layerId = ratingLayerId(pathId)
        val feature = PathGeoJsonMapper.ratingPathToFeature(path)
        val collection = if (feature != null) {
            FeatureCollection.fromFeature(feature)
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style.addSource(GeoJsonSource(sourceId, collection, GeoJsonOptions().withLineMetrics(true)))
        val layer = LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineWidth(lineWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        applyGradientOrFallback(layer, path)
        style.addLayerBelow(layer, LAYER_SAVED_COMMON)
    }

    private fun updateRatingLayer(style: Style, path: MapRatingPath) {
        val sourceId = ratingSourceId(path.pathId)
        val layerId = ratingLayerId(path.pathId)
        val feature = PathGeoJsonMapper.ratingPathToFeature(path)
        val collection = if (feature != null) {
            FeatureCollection.fromFeature(feature)
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(collection)
        val layer = style.getLayerAs<LineLayer>(layerId) ?: return
        applyGradientOrFallback(layer, path)
    }

    private fun removeRatingLayer(pathId: Long) {
        val style = this.style ?: return
        style.removeLayer(ratingLayerId(pathId))
        style.removeSource(ratingSourceId(pathId))
    }

    private fun applyGradientOrFallback(layer: LineLayer, path: MapRatingPath) {
        val stops = PathGradientStopsBuilder.build(path, blendMeters)
        if (stops.isEmpty()) {
            layer.setProperties(PropertyFactory.lineColor(color(SegmentRating.NONE)))
        } else {
            layer.setProperties(PropertyFactory.lineGradient(gradientExpression(stops)))
        }
    }

    private fun pushCommon() {
        style?.getSourceAs<GeoJsonSource>(SOURCE_SAVED_COMMON)
            ?.setGeoJson(FeatureCollection.fromFeatures(commonFeatures.values.toList()))
    }

    private fun pushCurrent() {
        val feature = PathGeoJsonMapper.segmentsToFeature(CURRENT_PATH_ID, currentSegments)
        val collection = if (feature != null) {
            FeatureCollection.fromFeature(feature)
        } else {
            FeatureCollection.fromFeatures(emptyArray())
        }
        style?.getSourceAs<GeoJsonSource>(SOURCE_CURRENT)?.setGeoJson(collection)
        val style = this.style ?: return
        val layer = style.getLayerAs<LineLayer>(LAYER_CURRENT) ?: return
        val currentPath = MapRatingPath(pathId = CURRENT_PATH_ID, pathSegments = currentSegments.toList())
        applyGradientOrFallback(layer, currentPath)
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

    private fun gradientExpression(stops: List<GradientStop>): Expression {
        val colorStops = ArrayList<Expression>(stops.size * 2)
        for (stop in stops) {
            colorStops.add(Expression.literal(stop.progress))
            colorStops.add(Expression.color(color(stop.rating)))
        }
        return Expression.interpolate(
            Expression.linear(),
            Expression.lineProgress(),
            *colorStops.toTypedArray(),
        )
    }

    @ColorInt
    private fun color(rating: SegmentRating): Int =
        ratingColors[rating] ?: ratingColors.getValue(SegmentRating.NONE)

    private fun ratingSourceId(pathId: Long): String = "$SOURCE_SAVED_RATING_PREFIX$pathId"
    private fun ratingLayerId(pathId: Long): String = "$LAYER_SAVED_RATING_PREFIX$pathId"

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
