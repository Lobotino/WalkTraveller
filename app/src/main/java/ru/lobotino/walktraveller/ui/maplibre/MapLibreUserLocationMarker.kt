package ru.lobotino.walktraveller.ui.maplibre

import android.graphics.Bitmap
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

class MapLibreUserLocationMarker(private val iconBitmap: Bitmap) {

    private var style: Style? = null
    private var position: LatLng? = null
    private var bearing: Float = 0f

    fun onStyleLoaded(style: Style) {
        this.style = style
        style.addImage(IMAGE_ID, iconBitmap)
        style.addSource(GeoJsonSource(SOURCE_ID))
        style.addLayer(
            SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                PropertyFactory.iconImage(IMAGE_ID),
                PropertyFactory.iconRotate(Expression.get(PROPERTY_BEARING)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        push()
    }

    fun setPosition(position: LatLng) {
        this.position = position
        push()
    }

    fun setRotation(bearing: Float) {
        this.bearing = if (bearing >= 360f) bearing % 360f else bearing
        push()
    }

    private fun push() {
        val current = position ?: return
        val feature = Feature.fromGeometry(
            Point.fromLngLat(current.longitude, current.latitude)
        ).apply { addNumberProperty(PROPERTY_BEARING, bearing) }
        style?.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(feature)
    }

    companion object {
        private const val IMAGE_ID = "wt-user-marker-image"
        private const val SOURCE_ID = "wt-user-marker-source"
        private const val LAYER_ID = "wt-user-marker-layer"
        private const val PROPERTY_BEARING = "bearing"
    }
}
